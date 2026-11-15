# Thesaurus and word list for ePUB Feed

Status: built in 1.10, installed and verified on the phone. One step is still outstanding: the
dictionary pack has to be uploaded as a release asset (see the end of this file).

## What this is

A dictionary that lives inside the reading flow. You long press a post, choose "Look up a word",
pick a word from that post with a selection handle, and get one short definition, the synonyms, a
plain rarity band, and where else the word appears in the book. One tap saves it to a word list you
own. The word list is a real screen with sorting, custom entries, and an export, and it never
depends on a network or on a downloaded dictionary to render what it already holds.

The dictionary itself ships as a downloadable pack (English first) because that is what makes the
feature work on a train, and because the free online word APIs are either English only, dated, or
about to want an API key. Online lookup stays available as an optional, off by default extra.

## The flow, screen by screen

### 1. Post actions sheet

One new entry, placed between "Add a note" and "Copy text":

    Look up a word
    Check a word in this post

Icon: an open book. Existing sheet order stays: chapter tool, note, lookup, copy. Nothing else
changes on this sheet.

### 2. Lookup screen (full screen, not a sheet)

Receives the post it was opened from. It shows the post as a reading column, same type size and
family as the feed, with the post's chapter and post marker in the top bar. No editing tools, no
keyboard, nothing to type.

Selecting: the text is a read only text field, so a long press gives the usual selection handles.
The selection snaps to whole words, so dragging a handle never leaves half a word selected, and
apostrophes and hyphens stay inside the word. One rule: the field is read only, never editable. A
paragraph of a book is not a document to edit.

Confirm step: no separate dialog and no "confirm" button in the text. When a selection exists, a bar
rises from the bottom with the selected word at reading size and one filled pill "Look up". The
selection is the confirmation. Clearing the selection dismisses the bar.

Results replace the bar, in one card:

    gloaming                                     rare
    the time of day immediately following sunset

    twilight   dusk   nightfall   evenfall   gloam

    3 uses in this book, chapters 2 and 7

    [ Save to word list ]

Rules for that card: the word is the headline, one definition only (the first sense of the most
likely part of speech), at most five synonyms as plain text chips, the rarity band is text, not a
colour dot, and the in book line appears only when the count is above one. If the word is already
saved, the pill reads "Saved" and is disabled. If nothing is found: "No entry for {word}" plus the
"Add it yourself" link that drops the word into the word list as a custom entry.

### 3. Word list screen

Route `words`, opened from the drawer. Full screen, not a sheet: it has its own sorting, its own
adding, and it is a place you return to.

Top bar: back arrow, title "Word list", a plus icon on the right that opens the add sheet. Under the
title in the bar, muted 13sp: "42 words, 7 this week". That is information, not decoration.

Sort control: a three segment row directly under the top bar, full width, 48dp tall. Newest first,
oldest first, by book. The selected segment is white on accent with ink text, never a second solid
fill. The choice persists in DataStore next to `collectionOrder`.

Row anatomy, newest first and oldest first:

    48dp min height, word 17sp semibold, one line
    definition under it, 14sp muted, two lines max, ellipsis
    20dp book avatar at the right edge of the word line
    relative time is not shown, the sort already answers "when", and the detail sheet carries
    the exact date and the chapter

By book: group headers of 24dp avatar plus the book title at 15sp semibold, words listed under each
group with no repeated avatar. Groups ordered by library order, so the drawer and this screen agree.

Tap a row: a detail sheet with the full definition, all synonyms, the rarity band, "Chapter 4 of
{book}" with the sentence the word came from, "Open the post" (jumps into the feed at that post),
"Look up again" and "Remove". Removing shows an undo snackbar, no confirmation dialog for a single
word.

Add sheet: word field, optional definition field, Save. With a pack installed the definition is
filled in on save if the field is empty, and the sheet shows the result before it is saved.

Empty state: "No words yet. Long press a post, choose Look up a word, then save what you find."
Nothing else. No illustration.

### 4. Drawer entry

Below the book rows and above "Add book", with the list icon, 14dp vertical padding to match the Add
book row: "Word list" with the count at the right in muted 13sp. It sits above the divider and the
version footnote, as asked.

## Where definitions come from

Two sources behind one interface, `ThesaurusSource`, chosen at lookup time.

Offline pack, the default:

- Built from WordNet 3.1 plus a frequency list: 117,791 synsets, 207,235 word to synset links,
  200,000 frequency rows. 27.7MB as a SQLite file, 13.9MB gzipped. The reason it is bigger than the
  first estimate: the frequency list runs to 200,000 words so that a word like "gloaming" gets a
  rarity at all, and the part of speech tie break needs its own table.
- Real query output from that build: "gloaming" gives "the time of day immediately following sunset"
  with twilight, dusk, gloam, nightfall, evenfall. "house" gives its five main senses.
- WordNet licence is permissive and needs the notice shipped in an About or licences screen.
- Distribution: the pack is a release asset on the existing GitHub release, downloaded on demand.
  No new service, no key, works with the release tooling that already ships the APK. A settings row
  shows "English dictionary, installed, 23MB" with Download and Remove. Verify sha256 and pack
  version after download, open read only from `filesDir/dictionaries/en-v1.db`.
- Pack files are plain SQLite opened read only, never a Room migration. Downloading or removing a
  language stays file management.

Online, optional, default off:

- Verified today: dictionaryapi.dev is keyless and returns definitions, synonyms and phonetics for
  English. Wiktionary REST returns glosses for any language code but as HTML. OpenThesaurus covers
  German keyless. Datamuse is keyless today but its own page states an API key is required from
  1 February 2027, so nothing should be built on keyless Datamuse.
- So online exists for the words WordNet lacks (slang, modern senses, names). A switch in settings,
  "Use online lookup", with one sentence about what leaves the device. Off, the app makes no network
  call, ever, for this feature.
- Resolver order: online when the switch is on and the network answers, pack otherwise, and a clear
  "no dictionary installed" state with a Download button when neither is available.

Rarity, without inventing numbers:

- Frequency data is a word to rank table, separate from WordNet. wordfreq's small English list stops
  at 28,511 words, so it cannot carry rarity alone. The pack uses Norvig's count_1w (333,333 words)
  trimmed to the top 200,000, which still covers gloaming at rank 185,304. The Google Books ngram
  terms ask for acknowledgement.
- Display a band, never a percentage: common, uncommon, rare, very rare, from fixed rank quantiles.
  A word outside the table is "rare" and says so honestly.

Definitions are copied into the word list at save time. The list renders with no pack installed and
no network, which is the whole point of having a list.

## Data model

Room schema v4, `MIGRATION_3_4`, `app/schemas/.../4.json` committed.

    words
      id            TEXT PK
      term          TEXT   the word as saved
      termKey       TEXT   lowercase, used for dedupe and lookup
      definition    TEXT
      partOfSpeech  TEXT
      synonyms      TEXT   comma separated, capped at five
      rarity        TEXT
      source        TEXT   PACK, ONLINE, CUSTOM
      packVersion   TEXT
      bookId        TEXT nullable     no foreign key, on purpose
      bookTitle     TEXT              snapshot at save time
      bookHue       INTEGER           snapshot, for the letter avatar fallback
      postId        TEXT nullable
      chapterIndex  INTEGER
      excerpt       TEXT              the sentence the word came from
      createdAt     INTEGER
    unique index on (termKey, bookId)

The no foreign key rule is the "never lose my words" rule. `SampleBooks.ensureSample` deletes every
`isSample` book when `SEED_VERSION` is bumped, and deleting a book cascades to posts, notes and
reactions. A word saved from the onboarding book would go with it. Words survive with a dangling
`bookId`, and the list falls back to the snapshot title and hue for the avatar, so a deleted book
still says where the word came from.

Data survives APK updates because the signing key does not change, so `install -r` keeps app data.
The real risks are an uninstall or a cleared data directory, which is what the export is for:
`Word list > Export` writes JSON through ACTION_CREATE_DOCUMENT into whatever folder the user picks,
and `Import` merges by (termKey, bookId, createdAt). No background sync, no account, no server.

New DataStore keys: `wordSortOrder`, `useOnlineLookup`, `thesaurusPackVersion`.

## Lookup details that will otherwise bite

- Word snapping needs a real word boundary regex, `\p{L}[\p{L}'’-]*\p{L}` plus single letters, so a
  selection cannot end on a half word and "don't" and "well-known" stay whole.
- Morphology before giving up: strip s, es, ed, ing, ly, handle y to ies, and a short irregular verb
  table (ran to run, went to go, better to good). Without it, half of what a reader highlights misses.
- Multiword selections: if the user selects two words, look up the phrase first, then the single
  words. Do not silently look up only the first word.
- In book usage count is a plain query over the posts already in Room for that book, word boundary
  matched, chapter index collected for the "chapters 2 and 7" line. No new index needed at this size.
- The pack query is two lookups: word to synset ids ordered by sense number, then synset to gloss and
  sibling lemmas. Sub millisecond on the built pack. Cap it at three senses in the query and show one.

## Out of scope for 1.10

No pronunciation audio, no example sentences, no spaced repetition, no tags or decks, no Anki export,
no German pack in this release (the pipeline and the pack format are language tagged so a German pack
built from ODeNet, CC BY-SA 4.0, is data and not code, later), no lookup from the note composer, no
system share target.

## Effort, lean

Half a week of real work, split so the risky part comes last:

1. Room v4, DAO, word list screen with sorting, custom add, remove with undo, drawer entry: 1.5 days.
2. Lookup screen with selection and result card, sheet action, navigation: 1 day.
3. Pack build script (tools/build-thesaurus-pack.py, same pattern as make-sample-epub.py), rarity
   table, licence screen, download and remove flow, settings row: 1 day.
4. Optional online source behind the switch: 0.25 day.
5. Export and import, unit tests (morphology, pack query against a tiny fixture pack, migration 3
   to 4), device pass on the phone: 0.75 day.

The word list and lookup UI can ship in 2.5 days with online lookup only, but shipping the offline
pack first is the version that stands on its own, and the pack build is one script plus one release
asset rather than a service.

## Decisions taken

1. The full pack ships. 13.9MB over the wire, 27.7MB on disk, and the settings row says 13 MB.
2. The book filter is a row of pills under the sort control: "All books" then every book that has
   words. It hides itself when there is only one, and it slides sideways when the library is long.
3. The drawer row is a plain "Word list". No count.
4. Version 1.10, versionCode 11.

## What was built

- `tools/build-thesaurus-pack.py`: downloads WordNet and the frequency list, writes the pack, prints
  its SHA-256, and can query an existing pack (`--check serendipity gloaming`).
- `data/thesaurus/`: `WordPack` interface, `SqliteWordPack` (read only, opened from filesDir),
  `PackManager` (download with progress, hash check, remove), `OnlineThesaurus` (opt in, keyless),
  `ThesaurusRepository` (pack first, online second).
- `domain/thesaurus/`: `WordForms` (word characters, snapping, sentences, morphology), `Rarity`
  bands, `ThesaurusEntry`, `WordUses`.
- Room v4 with `words`, no foreign key, unique on (termKey, bookId), plus `MIGRATION_3_4` and the
  committed `4.json`.
- `ui/lookup/`: the lookup screen and its view model.
- `ui/words/`: the word list, the add sheet, the detail sheet, the view model.
- Drawer row, navigation route `words`, settings section with the download and the online switch.
- 14 new unit tests: snapping and morphology, rarity bands, the use line, and the online payload.

Verified on the phone, not just compiled: the v3 to v4 migration on a library with 9 books and
11,339 posts (all likes, notes and positions intact), the drawer row, the sheet action being
disabled on a picture post, selecting "impossible" from a real post, the offline definition and its
"uncommon" band, saving it, the word list, sorting by book, the detail sheet, adding a word by hand,
removing with undo, export to a JSON file, import merging without duplicates, and a word whose book
is gone still listing under the book's name with a letter avatar.

## Still outstanding

The pack has to be published as a release asset before the Download button works on a fresh install:

    tag:   thesaurus-en-v1
    file:  dist/thesaurus-en-v1.db.gz
    url:   https://github.com/simisonne/epub-feed/releases/download/thesaurus-en-v1/thesaurus-en-v1.db.gz
    sha256 of the unpacked file:
           d85630d73ae678f4131714307ccada99d54973c70da118e6af9339b241eeb51f

`PackManager.English` holds that URL and that hash. If the pack is ever rebuilt, both change together.

## 1.11: what changed and why

**Online lookup is gone.** The switch, `OnlineThesaurus`, its test and every mention of it were
removed. It answered in English only, from one keyless endpoint, and it was the only reason a
lookup could behave differently from one session to the next. The app now has exactly one answer
path: the installed packs. The `INTERNET` permission stays for the pack download alone.

**A word with no entry belongs to its book.** Pressing *Keep the word anyway* used to call
`addCustom`, which wrote a row with no book, so the word left the book it came from and joined the
custom group. `WordRepository.saveWithoutEntry` writes the book, post, chapter and sentence, and
`Word.custom` is now simply "no book id" rather than "no dictionary source". The miss card also has
a saved state, so pressing the button once replaces it with *Saved to your word list*.

**Order: A to Z by default, then rarity.** `WordSort` is A to Z, Rarity, Newest, By book. Rarity
sorts rarest first, because the list records what was worth stopping for. A band is only four steps
wide, so the pack now reports the raw frequency position (`Frequency(rank, total)`) and the word
keeps it (`words.freqRank`, `words.freqTotal`, `MIGRATION_4_5`); the sort compares rank over total,
which keeps two packs on different scales comparable, then the alphabet. Words saved before 1.11
have no rank and sort last until they are looked up again.

**Every language, not just English.** WordNet only exists in English, so `tools/build-thesaurus-pack-wiktionary.py`
builds a pack per language from that language's own Wiktionary edition (kaikki.org extracts, CC BY-SA)
with ranks from FrequencyWords (OpenSubtitles). The German pack is built this way, which is the only
way to get German definitions of German words: the English Wiktionary's German extract glosses them
in English. `PackManager` now holds a spec per language, downloads and removes each one separately,
and `openAll()` asks the reader's language first and the rest after it. Three packs exist: English
(WordNet), German (dewiktionary) and Korean (kowiktionary, which glosses Korean in Korean and holds
75,891 words). Adding a fourth is a `LANGUAGES` entry in the builder plus a `PackSpec`, and the
release asset is the only outside step.

Two download bugs surfaced while testing the packs on a phone, both fixed in 1.11: a finished
download left `downloading` true, so the row sat on "Downloading, 100%" and every later attempt was
refused as already running; and the failure an `Already downloading` or denied permission returned
never reached the row. `readStatus` preserves the in flight flag on purpose, so the success path now
clears it by hand. Android 17 also hands `INTERNET` out as a runtime permission, so the settings
screen asks for it when Download is pressed and says so if it is refused.

## Search inside a book

`domain/search/BookSearch.kt` is pure Kotlin and unit tested; `SearchRepository` loads the book's
plain text in one query and matches in memory on `Dispatchers.Default`.

- Every word of the query must appear in a post as a whole word, so "art" never matches "earth".
- Words are matched through `WordForms.candidates`, so "houses" finds "house" and "ran" finds "run",
  but a post that holds the exact word always outranks one that only holds a guess.
- A query of adjacent words in order earns the biggest bonus: the post that says "the glass bead
  game" beats the one that mentions the three words far apart.
- Ties break by position in the book, so results read in reading order.
- If no post holds every word, the pass re-runs as "any word" and the screen says so instead of
  showing an empty list while a word is clearly in the book.
- Korean is the one exception to the whole word rule: particles are glued on, so 사랑 matches
  사랑은, 사랑이 and 사랑을 (start boundary only, `WordForms.isHangul`). Without it almost nothing in
  a Korean book is reachable.
- The status line counts every match the book holds, `SearchResult.total`, not the first page that
  the 200 hit cap lets through.
- Each hit shows the best sentence, cut to a card sized window, with the matched ranges marked. The
  marks are computed against the excerpt, so the highlight lands on the words the matcher found
  rather than on a second string search.
