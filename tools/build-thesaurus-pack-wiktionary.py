#!/usr/bin/env python3
"""Build an offline dictionary pack for a language from a Wiktionary extract.

The English pack comes from WordNet (tools/build-thesaurus-pack.py). WordNet only exists in
English, so every other language is built from Wiktionary, which does gloss words in their own
language. The output has exactly the same schema as the WordNet pack, so the app reads both with
SqliteWordPack and adding a language is a spec entry plus a build.

Inputs (downloaded once into build/thesaurus-source, both freely redistributable):
  kaikki.org word extract of that language's Wiktionary (CC BY-SA, derived from Wiktionary)
  FrequencyWords list from hermitdave/FrequencyWords (OpenSubtitles counts), for the rarity ranks

Usage:
  python3 tools/build-thesaurus-pack-wiktionary.py --language de
  python3 tools/build-thesaurus-pack-wiktionary.py --language de --limit-lines 200000   # quick pass
  python3 tools/build-thesaurus-pack-wiktionary.py --check dist/thesaurus-de-v1.db Glück Klangfarbe

Adding a language: add an entry to LANGUAGES. The extract URL is the language name kaikki uses
and the frequency file lives under content/2018/<code>/<code>_50k.txt.
"""
import argparse
import gzip
import hashlib
import json
import os
import sqlite3
import sys
import urllib.request
from urllib.parse import quote

UA = "epub-feed-thesaurus-builder/1.0"
KAIKKI_URL = "https://kaikki.org/{path}/words/kaikki.org-dictionary-{name}-words.jsonl"
FREQ_URL = (
    "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/"
    "{code}/{code}_50k.txt"
)
SOURCE_DIR = "build/thesaurus-source"
GLOSS_LIMIT = 140
POS_CODES = {"noun": 0, "verb": 1, "adj": 2, "adv": 3}
POS_NAMES = {0: "noun", 1: "verb", 2: "adjective", 3: "adverb"}

# Each language comes from its own Wiktionary edition, because that edition glosses its words in
# its own language. The English Wiktionary's "German" extract defines German words in English,
# which is not what a reader inside a German book wants.
LANGUAGES = {
    "de": {"label": "German", "path": "dewiktionary/Deutsch", "name": "Deutsch", "version": "de-v1"},
    "fr": {"label": "French", "path": "frwiktionary/Français", "name": "Français", "version": "fr-v1"},
    "es": {"label": "Spanish", "path": "eswiktionary/Español", "name": "Español", "version": "es-v1"},
    "it": {"label": "Italian", "path": "itwiktionary/Italiano", "name": "Italiano", "version": "it-v1"},
    "ko": {"label": "Korean", "path": "kowiktionary/한국어", "name": "한국어", "version": "ko-v1"},
}


def download(url: str, path: str) -> str:
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    print(f"  fetching {url}")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=180) as r, open(path, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    return path


def short_gloss(gloss: str) -> str:
    gloss = " ".join(gloss.split())
    gloss = gloss.rstrip(" ;,.")
    if len(gloss) <= GLOSS_LIMIT:
        return gloss
    head = gloss[:GLOSS_LIMIT]
    space = head.rfind(" ")
    return (head[:space] if space > 60 else head).rstrip(" ,;.") + "\u2026"


def frequency_table(code: str) -> list[tuple[str, int]]:
    path = download(FREQ_URL.format(code=code), os.path.join(SOURCE_DIR, f"{code}-50k.txt"))
    rows = []
    with open(path, encoding="utf-8") as f:
        for rank, line in enumerate(f, start=1):
            parts = line.split()
            if not parts:
                continue
            word = parts[0].strip().lower()
            if word:
                rows.append((word, rank))
    return rows


def is_single_word(word: str) -> bool:
    """Multi word phrases and numbers would swell the pack and rarely answer a selection."""
    if not word or len(word) > 40:
        return False
    if " " in word or "-" in word and len(word) > 24:
        return False
    return all(c.isalpha() or c in "-'\u2019" for c in word)


def is_form_of(sense: dict) -> bool:
    """Inflected forms point at their lemma, which the list holds anyway, so they are skipped."""
    if sense.get("form_of") or sense.get("alt_of"):
        return True
    glosses = sense.get("glosses") or []
    if not glosses:
        return True
    first = glosses[0].lower()
    return first.startswith(("form of", "inflection of", "plural of", "past of", "participle of"))


def build(code: str, out: str, line_limit: int | None, frequent_only: bool) -> None:
    info = LANGUAGES[code]
    print(f"dictionary pack {info['version']}")
    words_path = download(
        KAIKKI_URL.format(path=quote(info["path"]), name=quote(info["name"])),
        os.path.join(SOURCE_DIR, f"{code}-{code}-words.jsonl"),
    )
    freq = frequency_table(code)
    ranks = {word: rank for word, rank in freq}
    print(f"  frequency list: {len(ranks)} words")

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

    synsets = []
    senses = []
    seen = set()
    next_id = 1
    entries = 0
    kept = 0
    with open(words_path, encoding="utf-8") as f:
        for number, line in enumerate(f, start=1):
            if line_limit is not None and number > line_limit:
                break
            entries += 1
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            word = (entry.get("word") or "").strip()
            if not is_single_word(word):
                continue
            key = word.lower()
            # Every word the edition defines, not only the frequent ones: compounds such as
            # "Klangfarbe" are exactly what a reader stops on, and they sit far outside a 50k list.
            # The frequency list still decides the rarity band, it just no longer decides entry.
            if frequent_only and key not in ranks:
                continue
            pos = POS_CODES.get(entry.get("pos"))
            if pos is None:
                continue
            seq = 0
            added = False
            for sense in entry.get("senses") or []:
                if is_form_of(sense):
                    continue
                gloss = short_gloss(sense["glosses"][0])
                if not gloss or len(gloss) < 3:
                    continue
                markers = [word]
                for synonym in sense.get("synonyms") or []:
                    other = (synonym.get("word") or "").strip()
                    if not other or other.lower() == key or " " in other:
                        continue
                    if other.lower() not in {m.lower() for m in markers}:
                        markers.append(other)
                signature = (key, gloss)
                if signature in seen:
                    seq += 1
                    continue
                seen.add(signature)
                synsets.append((next_id, pos, " ".join(markers), gloss))
                senses.append((key, next_id, seq))
                next_id += 1
                seq += 1
                added = True
                if seq >= 3:
                    break
            if added:
                kept += 1

    db.executemany("INSERT INTO synsets VALUES (?,?,?,?)", synsets)
    db.executemany("INSERT INTO senses VALUES (?,?,?)", senses)
    db.execute("CREATE INDEX senses_lemma ON senses(lemma)")
    db.executemany("INSERT INTO freq VALUES (?,?)", freq)
    db.execute("INSERT INTO freq_total VALUES (?)", (len(freq),))
    db.executemany(
        "INSERT INTO meta VALUES (?,?)",
        [
            ("version", info["version"]),
            ("language", code),
            ("synsets", str(len(synsets))),
            ("senses", str(len(senses))),
            ("frequency", str(len(freq))),
            ("sources", f"{info['label']} Wiktionary via kaikki.org, CC BY-SA; "
                        "FrequencyWords (OpenSubtitles) for the ranks"),
        ],
    )
    db.commit()
    db.execute("VACUUM")
    db.execute("PRAGMA user_version = 1")
    db.commit()
    db.close()

    size = os.path.getsize(out)
    sha = hashlib.sha256(open(out, "rb").read()).hexdigest()
    print(f"  entries read: {entries}, words kept: {kept}")
    print(f"  wrote {out}: {size / 1e6:.1f} MB")
    print(f"  sha256 {sha}")
    print(f"  gzip:  gzip -9 -k -c {out} > {out}.gz")


def check(pack: str, words: list[str]) -> None:
    db = sqlite3.connect(f"file:{pack}?mode=ro", uri=True)
    total = db.execute("SELECT rows FROM freq_total").fetchone()[0]
    for word in words:
        key = word.lower()
        row = db.execute("SELECT rank FROM freq WHERE word = ?", (key,)).fetchone()
        rarity = "no frequency entry"
        if row:
            rarity = f"rank {row[0]:,} of {total:,} ({row[0] / total * 100:.1f}%)"
        print(f"\n{word}  {rarity}")
        rows = db.execute(
            "SELECT s.pos, s.lemmas, s.gloss FROM senses e "
            "JOIN synsets s ON s.id = e.synset WHERE e.lemma = ? ORDER BY e.seq LIMIT 3",
            (key,),
        ).fetchall()
        if not rows:
            print("  no entry")
        for pos, lemmas, gloss in rows:
            others = [w for w in lemmas.split(" ") if w.lower() != key][:5]
            print(f"  {POS_NAMES[pos]}: {gloss}")
            if others:
                print(f"    synonyms: {', '.join(others)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", default="de", choices=sorted(LANGUAGES))
    parser.add_argument("--out", default=None)
    parser.add_argument("--limit-lines", type=int, default=None)
    parser.add_argument(
        "--frequent-only",
        action="store_true",
        help="keep only words from the frequency list (a much smaller pack)",
    )
    parser.add_argument("--check", nargs="*", help="query an existing pack instead of building")
    args = parser.parse_args()
    out = args.out or f"dist/thesaurus-{LANGUAGES[args.language]['version']}.db"
    if args.check is not None:
        check(out, args.check)
        return 0
    build(args.language, out, args.limit_lines, args.frequent_only)
    return 0


if __name__ == "__main__":
    sys.exit(main())
