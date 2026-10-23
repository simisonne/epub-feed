# ePUB Feed

An offline Android reader that turns a book into an X style timeline. One chapter is one
feed: the chapter title opens the thread, paragraphs become posts, and when the chapter
runs out a single blue button takes you to the next one.

![Feed](docs/screenshots/feed.png)
![Chapter end](docs/screenshots/chapter-end.png)
![Note composer](docs/screenshots/note.png)
![Profile](docs/screenshots/profile.png)
![Chapters](docs/screenshots/chapters.png)

Kotlin, Jetpack Compose (Material 3, heavily rethemed), single activity, Navigation Compose,
Room, Hilt, Coroutines and DataStore. **No network permission at all.**

## What it does

**Reading**

- Imports EPUB and PDF. EPUB is parsed by hand (ZIP → `container.xml` → OPF manifest/spine →
  XHTML via Jsoup, chapter titles from the EPUB 3 nav document or EPUB 2 NCX); PDF text comes
  from pdfbox-android, split on its outline.
- The parsed book is stored in Room, so reopening never touches the original file again.
- One chapter per feed, with a thread line down the avatar column and a "Go to next chapter"
  button where a timeline would offer a refresh.
- Remembers exactly where you stopped, per book, and restores it on launch.
- Chapters tab with per chapter progress. Post length (short / medium / long) is generated at
  import time and can be changed later without losing your place, likes or notes.
- Lights out / Dim / Light themes, S / M / L text, serif or sans.

**Writing**

- Tap the speech bubble under any post to leave a note: a 280 character composer that shows
  the post you are replying to and the notes already on it. Notes render under the post.
- Likes (pink, with a pop and haptics), bookmarks, and separate **Likes** and **Saved** tabs.
  Tapping an entry in either tab jumps the feed back to that post.

**Fixing the book**

- Long press any post for the context menu. **Mark as chapter** makes that post the start of a
  new chapter (for books whose chapter detection got it wrong); on a chapter's first post the
  same entry reads **Unmark as chapter** and folds it back into the previous chapter. Both
  re-slice the book, keep likes, bookmarks and notes on the same source text, and park the
  feed on the post you touched.

**Profile**

- Tapping the poster's avatar opens the book's profile page: cover, title, author, publisher
  and year (from the EPUB metadata), chapter / word / post counts, an estimated page count,
  read progress, likes, saves, notes, and reading time.
- Reading time is tracked while you are actually reading and **pauses after two minutes
  without a touch**, so leaving the app open on the table does not inflate it.

**Library**

- A slide out drawer lists the books on the device, with the app version at the bottom.
- A bundled sample (Pride and Prejudice, public domain) is seeded on first launch so the app
  is never an empty screen.
- Swipe left and right between the Feed, Chapters, Likes and Saved tabs.

## Build

Requirements: JDK 21, Android SDK with platform 37 and build-tools 36, an `adb` connection for
installing.

```
git clone https://github.com/simisonne/epub-feed.git
cd epub-feed
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests (post generation, EPUB parsing, PDF parsing) run on the JVM, no device needed:

```
./gradlew testDebugUnitTest
```

## How it works

- `data/epub` and `data/pdf` turn a file into chapters of `Block`s (paragraph / quote /
  heading), keeping inline bold and italic runs.
- `domain/PostGenerator` groups blocks into posts under a character budget, never cutting a
  paragraph in half unless it is far over budget, in which case it splits on sentence
  boundaries with an abbreviation aware splitter.
- Room keeps `books`, `chapters` (the source blocks, so posts can be regenerated), `posts`
  (with their source block range), `reactions`, `notes` and `book_stats`.
- Anything the reader attaches to a post is anchored to a **book wide source block offset**,
  which is what lets a chapter split, a chapter merge or a different post length carry likes,
  bookmarks and notes over to the right place.
- The feed is a `LazyColumn` per chapter; the list's index is the post's position in the
  chapter, so the reading position is always exact.

## Privacy

Everything stays on the device. The app declares no `INTERNET` permission, has no analytics
and no accounts. Backups are disabled on purpose (`allowBackup="false"`).

## Known limits

- PDF chaptering depends on the PDF having an outline; without one the whole document becomes
  a single chapter, which is where "Mark as chapter" earns its keep.
- Publisher, year and page counts are read from metadata or estimated (≈275 words per page);
  EPUBs rarely state real page counts.
- Very large chapters are loaded whole, because a chapter is a feed.

## License

MIT, see [LICENSE](LICENSE).
