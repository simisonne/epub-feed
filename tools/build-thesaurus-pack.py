#!/usr/bin/env python3
"""Build the offline English thesaurus pack the app downloads on demand.

Inputs (downloaded once into build/thesaurus-source, both freely redistributable):
  WordNet 3.1 dictionary files, Princeton, WordNet licence (notice ships in the app)
  Google Books word counts, the "count_1w" list published by Peter Norvig

Output: a single SQLite file the app opens read only:

  meta(key TEXT PRIMARY KEY, value TEXT)          version, language, sources, counts
  synsets(id INTEGER PRIMARY KEY, pos INTEGER, lemmas TEXT, gloss TEXT)
  senses(lemma TEXT, synset INTEGER, seq INTEGER)  seq 0 is the most common sense
  pos_tags(lemma TEXT, pos INTEGER, tagged INTEGER)  how often a sense of that POS was tagged
  freq(word TEXT PRIMARY KEY, rank INTEGER)        rank 1 is the most common word
  freq_total(rows INTEGER)                          how many words the ranks span

The gloss is trimmed to its first clause so a phone card shows one short definition
instead of a paragraph with a usage example. Nothing else about WordNet is changed.

Usage:
  python3 tools/build-thesaurus-pack.py            # build dist/thesaurus-en-v1.db
  python3 tools/build-thesaurus-pack.py --limit 60000
  python3 tools/build-thesaurus-pack.py --check serendipity gloaming house
"""
import argparse
import hashlib
import os
import sqlite3
import sys
import tarfile
import urllib.request

UA = "epub-feed-thesaurus-builder/1.0"
WORDNET_URL = "https://registry.npmjs.org/wordnet-db/-/wordnet-db-3.1.14.tgz"
FREQ_URL = "https://norvig.com/ngrams/count_1w.txt"
SOURCE_DIR = "build/thesaurus-source"
DEFAULT_OUT = "dist/thesaurus-en-v1.db"
PACK_VERSION = "en-v1"
POS_NAMES = {0: "noun", 1: "verb", 2: "adjective", 3: "adverb"}
POS_CODES = {"n": 0, "v": 1, "a": 2, "s": 2, "r": 3}
GLOSS_LIMIT = 140


def download(url: str, path: str) -> str:
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    print(f"  fetching {url}")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    return path


def wordnet_dict() -> str:
    tgz = download(WORDNET_URL, os.path.join(SOURCE_DIR, "wordnet-db.tgz"))
    out = os.path.join(SOURCE_DIR, "wordnet")
    if not os.path.isdir(os.path.join(out, "dict")):
        os.makedirs(out, exist_ok=True)
        with tarfile.open(tgz) as tar:
            members = [m for m in tar.getmembers() if m.name.startswith("package/dict/")]
            tar.extractall(out, members=members, filter="data")
        os.rename(os.path.join(out, "package", "dict"), os.path.join(out, "dict"))
    return os.path.join(out, "dict")


def frequency_table(limit: int) -> list[tuple[str, int]]:
    path = download(FREQ_URL, os.path.join(SOURCE_DIR, "count_1w.txt"))
    rows = []
    with open(path, encoding="utf-8") as f:
        for rank, line in enumerate(f, start=1):
            if len(rows) >= limit:
                break
            word = line.split("\t")[0].strip().lower()
            if word:
                rows.append((word, rank))
    return rows


def short_gloss(gloss: str) -> str:
    gloss = " ".join(gloss.split())
    # "sense; \"a usage example\"" -> the sense alone.
    cut = gloss.find('; "')
    if cut > 0:
        gloss = gloss[:cut]
    gloss = gloss.rstrip(" ;,.")
    if len(gloss) <= GLOSS_LIMIT:
        return gloss
    head = gloss[:GLOSS_LIMIT]
    space = head.rfind(" ")
    return (head[:space] if space > 40 else head).rstrip(" ,;.") + "\u2026"


def parse_synsets(dict_dir: str):
    for name in ("data.noun", "data.verb", "data.adj", "data.adv"):
        with open(os.path.join(dict_dir, name), encoding="latin-1") as f:
            for line in f:
                if line.startswith("  "):
                    continue
                head, _, gloss = line.partition("|")
                tokens = head.split()
                if len(tokens) < 4:
                    continue
                offset = int(tokens[0])
                pos = POS_CODES[tokens[2]]
                count = int(tokens[3], 16)
                lemmas, i = [], 4
                for _ in range(count):
                    lemmas.append(tokens[i].replace("_", " ").lower())
                    i += 2
                yield pos * 100_000_000 + offset, pos, " ".join(lemmas), short_gloss(gloss)


def parse_senses(dict_dir: str):
    """index.<pos> lists each lemma's synsets in sense order, most common first."""
    for name in ("index.noun", "index.verb", "index.adj", "index.adv"):
        with open(os.path.join(dict_dir, name), encoding="latin-1") as f:
            for line in f:
                if line.startswith("  "):
                    continue
                tokens = line.split()
                if len(tokens) < 7:
                    continue
                lemma, pos = tokens[0].replace("_", " ").lower(), POS_CODES[tokens[1]]
                synset_count, pointer_count = int(tokens[2]), int(tokens[3])
                start = 4 + pointer_count + 2
                for seq, offset in enumerate(tokens[start:start + synset_count]):
                    yield lemma, pos * 100_000_000 + int(offset), seq


def parse_pos_tags(dict_dir: str):
    """tagsense_cnt per lemma and POS: which part of speech a word is actually used as."""
    for name in ("index.noun", "index.verb", "index.adj", "index.adv"):
        with open(os.path.join(dict_dir, name), encoding="latin-1") as f:
            for line in f:
                if line.startswith("  "):
                    continue
                tokens = line.split()
                if len(tokens) < 7:
                    continue
                lemma, pos = tokens[0].replace("_", " ").lower(), POS_CODES[tokens[1]]
                tagged = int(tokens[4 + int(tokens[3]) + 1])
                # Only the non zero counts matter: they are the tie break between parts of speech.
                if tagged > 0:
                    yield lemma, pos, tagged


def build(out: str, limit: int) -> None:
    print("thesaurus pack")
    dict_dir = wordnet_dict()
    freq = frequency_table(limit)
    print(f"  frequency list: {len(freq)} words")

    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    db.executescript(
        """
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT);
        CREATE TABLE synsets(id INTEGER PRIMARY KEY, pos INTEGER, lemmas TEXT, gloss TEXT);
        CREATE TABLE senses(lemma TEXT, synset INTEGER, seq INTEGER);
        CREATE TABLE pos_tags(lemma TEXT, pos INTEGER, tagged INTEGER);
        CREATE TABLE freq(word TEXT PRIMARY KEY, rank INTEGER);
        CREATE TABLE freq_total(rows INTEGER);
        """,
    )

    synsets = list(parse_synsets(dict_dir))
    db.executemany("INSERT INTO synsets VALUES (?,?,?,?)", synsets)
    print(f"  synsets: {len(synsets)}")

    tags = list(parse_pos_tags(dict_dir))
    db.executemany("INSERT INTO pos_tags VALUES (?,?,?)", tags)
    db.execute("CREATE INDEX pos_tags_lemma ON pos_tags(lemma)")

    senses = list(parse_senses(dict_dir))
    db.executemany("INSERT INTO senses VALUES (?,?,?)", senses)
    db.execute("CREATE INDEX senses_lemma ON senses(lemma)")
    print(f"  senses: {len(senses)}")

    db.executemany("INSERT INTO freq VALUES (?,?)", freq)
    db.execute("INSERT INTO freq_total VALUES (?)", (len(freq),))
    db.executemany(
        "INSERT INTO meta VALUES (?,?)",
        [
            ("version", PACK_VERSION),
            ("language", "en"),
            ("synsets", str(len(synsets))),
            ("senses", str(len(senses))),
            ("frequency", str(len(freq))),
            ("sources", "WordNet 3.1 (Princeton), Google Books counts via count_1w (Norvig)"),
        ],
    )
    db.commit()
    db.execute("VACUUM")
    db.execute("PRAGMA user_version = 1")
    db.commit()
    db.close()

    size = os.path.getsize(out)
    sha = hashlib.sha256(open(out, "rb").read()).hexdigest()
    print(f"  wrote {out}: {size / 1e6:.1f} MB")
    print(f"  sha256 {sha}")
    print(f"  gzip:  gzip -9 -k -c {out} > {out}.gz")


def check(pack: str, words: list[str]) -> None:
    db = sqlite3.connect(f"file:{pack}?mode=ro", uri=True)
    total = db.execute("SELECT rows FROM freq_total").fetchone()[0]
    for word in words:
        row = db.execute("SELECT rank FROM freq WHERE word = ?", (word.lower(),)).fetchone()
        rarity = "no frequency entry"
        if row:
            p = row[0] / total
            rarity = f"rank {row[0]:,} of {total:,} ({p * 100:.1f}%)"
        print(f"\n{word}  {rarity}")
        rows = db.execute(
            "SELECT s.pos, s.lemmas, s.gloss FROM senses e "
            "JOIN synsets s ON s.id = e.synset "
            "LEFT JOIN pos_tags t ON t.lemma = e.lemma AND t.pos = s.pos "
            "WHERE e.lemma = ? ORDER BY COALESCE(t.tagged, 0) DESC, e.seq LIMIT 3",
            (word.lower(),),
        ).fetchall()
        if not rows:
            print("  no entry")
        for pos, lemmas, gloss in rows:
            others = [w for w in lemmas.split(" ") if w != word.lower()][:5]
            print(f"  {POS_NAMES[pos]}: {gloss}")
            if others:
                print(f"    synonyms: {', '.join(others)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--limit", type=int, default=200_000, help="how many frequent words to keep")
    parser.add_argument("--check", nargs="*", help="query an existing pack instead of building")
    args = parser.parse_args()
    if args.check is not None:
        check(args.out, args.check)
        return 0
    build(args.out, args.limit)
    check(args.out, ["serendipity", "gloaming", "house", "ephemeral"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
