# Epub Feed

An offline Android reader that turns a book into an X style timeline. Every chapter
becomes a thread, every paragraph group becomes a post, and the book itself is the
"account" at the top of the feed.

Kotlin, Jetpack Compose (Material 3, heavily rethemed), single activity, Navigation
Compose, Room, Hilt, Coroutines and DataStore. No network permission at all.

## What it does

- Imports EPUB (parsed by hand: ZIP -> container.xml -> OPF manifest/spine -> XHTML via
  Jsoup, with the EPUB 3 nav document or EPUB 2 NCX for chapter titles) and PDF
  (pdfbox-android text extraction, outline aware chapter splitting).
- Stores the parsed book in Room, so a book opens instantly after the first import and
  never depends on the original file staying valid.
- Renders the feed natively in Compose: 40dp avatar column with a 2dp thread line per
  chapter, verified name, `@handle`, `Ch. n · post` meta, quote cards, italic runs.
- Likes (pink, with a scale pop and haptics) and bookmarks (blue), saved per post and
  persisted in Room.
- Continuous, debounced reading position save and exact restore on reopen.
- Feed / Chapters / Saved tabs, X style library drawer, `Aa` settings sheet,
  Lights out / Dim / Light themes, S/M/L text, serif or sans, short / medium / long posts.
- Pinned welcome post on the bundled sample book only.
- Windowed Room queries (`globalIndex BETWEEN`) plus stable list keys, so a 10,000 post
  book scrolls without loading the whole book into memory.

## Build

Requirements: JDK 17+ (JDK 21 is what this was built with), Android SDK with platform 36.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
`local.properties` is not committed; Android Studio writes it for you, or set
`ANDROID_HOME` as above.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open the app: the bundled sample (three chapters of Pride and Prejudice) is
imported on first launch. Tap the blue pencil FAB to import an EPUB or PDF, tap the
avatar for the library drawer, tap `Aa` for type and theme.

You can also "Open with" or share an `.epub` / `.pdf` to the app from any file manager.

## Tests

```bash
./gradlew testDebugUnitTest
```

Covers the EPUB parser (spine order, metadata, nav/NCX/heading/numbered chapter title
fallbacks, italics and quote extraction, DRM and malformed file errors, href
resolution), the post splitter (sentence boundaries, abbreviation handling,
determinism, stable block anchors, budget behaviour), and PDF handling (outline and
page-chunk chaptering, scanned PDF detection, hyphenation and page number cleanup,
plus a real PDF text extraction round trip through PDFBox).

## Layout

```
app/src/main/java/com/epubfeed
  data/       room entities, daos, repository, import manager, prefs
    epub/     EpubParser, HtmlBlocks
    pdf/      PdfTextSource, PdfBookParser, PdfTextSanitizer, PdfBoxTextSource
  domain/     Block/Run model, PostGenerator, SentenceSplitter, settings model
  ui/         theme, components (PostCard, FeedTopBar, drawer, sheets), feed screens
  di/         Hilt module
```

## Known limitations

- Scanned PDFs have no text layer, so they are rejected with an explanatory message.
  There is no OCR.
- PDF text is linearised: multi column layouts and tables can come out in reading order
  rather than visual order.
- EPUB footnote references are stripped, not linked.
- Only plain prose formatting is modelled: bold, italics and block quotes. Tables,
  images and math are dropped.
- DRM protected EPUBs and password protected PDFs are refused, not decrypted.

## Privacy

No INTERNET permission, no analytics, no accounts, no cloud sync. Books, covers, likes,
bookmarks and positions live in the app's private storage on the device.
