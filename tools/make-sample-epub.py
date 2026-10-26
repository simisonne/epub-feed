#!/usr/bin/env python3
"""Builds the bundled onboarding book: app/src/main/assets/sample-epub-feed-basics.epub.

The book is a real, valid EPUB so the app parses it through the same path as a user
import. Its cover is drawn from the launcher icon (three feed bars), and its chapters
walk through every feature the app has.

Run from the repo root:  python3 tools/make-sample-epub.py
"""

from __future__ import annotations

import io
import os
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "sample-epub-feed-basics.epub")

TITLE = "ePUB Feed Basics"
AUTHOR = "ePUB Feed"
PUBLISHER = "ePUB Feed"
DATE = "2026-10-23"

# The "Reading" chapter carries one picture, so the tour shows what a picture post looks like.
ILLUSTRATED_CHAPTER = 1

# Background and bars taken straight from res/drawable/ic_launcher_foreground.xml.
BG = "#0A0A0A"
ACCENT = "#1D9BF0"
PAPER = "#E7E9EA"

CHAPTERS = [
    (
        "Welcome",
        [
            "ePUB Feed turns a book into a timeline. Every chapter is a thread, every paragraph is a post, and you scroll it like a feed instead of turning pages.",
            "This little book is the guide that ships with the app. Read it, poke at it, mark it up. Nothing in here is precious.",
            "When you are done, import your own EPUB or PDF and it lands in the library next to this one.",
        ],
    ),
    (
        "Reading",
        [
            "Scroll the feed. Your place is saved as you go, so closing the app and coming back drops you on the same post in the same chapter.",
            "Pictures in a book arrive as picture posts of their own, drawn full width the way a photo lands on a timeline.",
            "The percentage under each post is how far through its own chapter that post sits. A long chapter counts its own posts, not the whole book.",
            "At the end of a chapter there is one button. It takes you to the next chapter and nothing else.",
            "The cover at the top left is the book you are reading. Tap it to open the library, which lists every book on the device and the app version at the bottom.",
            "Tap the cover next to any post to open that book's profile page.",
        ],
    ),
    (
        "Likes, saves and notes",
        [
            "Tap the heart to like a post. Tap the bookmark to save it. Both stay on this device.",
            "Liked posts and Saved posts each have their own tab. Tap an entry there and the feed jumps back to that post.",
            "Tap the reply icon to write a note. A note is yours, it stays on this device, and it sits under the post it belongs to.",
            "Notes are a good place for the line you want to remember later, or the argument you want to make with the author.",
            "Long press any post for the rest. Copy it with its attribution, write a note on it, or start a new chapter right there.",
        ],
    ),
    (
        "Chapters",
        [
            "The Chapters tab lists every chapter with its own progress bar. Tap a chapter to jump to it.",
            "Long press a chapter to unmark it. It folds back into the chapter before it, which is what you want when a book splits one chapter across several files.",
            "To go the other way, long press any post and choose Mark as chapter. That post becomes the start of a new chapter and the book is sliced again around it.",
            "Both directions keep your place, your likes and your notes. They are anchored to the source text, so nothing drifts when the book is re-sliced.",
            "This is how you fix a PDF that arrived as one very long chapter, or a book whose headings were never recognised.",
        ],
    ),
    (
        "Settings",
        [
            "The gear at the top right opens the reading settings. Nothing there changes the book, only the way it is drawn.",
            "Text size runs from S to L. Font is Sans or Serif. The sample sentence at the bottom of the sheet redraws as you tap, so you can see the change before you leave.",
            "Theme is Lights out, Dim or Light. Lights out is the default because a feed at night reads better dark.",
            "Post length decides how much text fits in one post, Short, Medium or Long. Changing it rewrites every post in the book in place.",
            "Your place, your likes and your notes all survive a post length change. They are anchored to the text, not to the post that happened to hold it.",
            "Try it. Set Long and watch this chapter turn into fewer, bigger posts.",
        ],
    ),
    (
        "The profile",
        [
            "Tap the book's avatar on any post, or the cover on the feed, to open the profile. It is the account page a book never had.",
            "The top row counts chapters, words and the share of the book you have read. The bar underneath draws the same number.",
            "Under that is what the book is, the publisher, the year, the format, the length in words and pages, and the day it arrived.",
            "The last block is yours. Tracked time, then the posts you liked, the posts you saved, and the notes you wrote.",
            "Tracked time only moves while you are reading. A minute of scrolling counts, an hour with the app open on the table does not.",
        ],
    ),
]


def cover_png() -> bytes:
    """The launcher mark on a dark cover: a blue bar over two paper bars."""
    width, height = 1200, 1600
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    bar_w = 720
    left = (width - bar_w) // 2
    heights = (120, 96, 96)
    gaps = (64, 56)
    radius = 26
    stack_h = sum(heights) + sum(gaps)
    y = (height - stack_h) // 2
    colors = (ACCENT, PAPER, PAPER)
    widths = (bar_w, bar_w - 24, int(bar_w * 0.62))
    for index in range(3):
        box = (left, y, left + widths[index], y + heights[index])
        draw.rounded_rectangle(box, radius=radius, fill=colors[index])
        y += heights[index]
        if index < 2:
            y += gaps[index]
    out = io.BytesIO()
    image.save(out, format="PNG", optimize=True)
    return out.getvalue()


def illustration_png() -> bytes:
    """One picture post: a dark plate with a blue block and two paper lines, like a feed card."""
    width, height = 1200, 800
    image = Image.new("RGB", (width, height), "#101214")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((72, 96, width - 72, height - 96), radius=40, fill="#16181C")
    draw.rounded_rectangle((132, 168, width - 132, 470), radius=28, fill=ACCENT)
    draw.rounded_rectangle((132, 528, width - 300, 566), radius=19, fill=PAPER)
    draw.rounded_rectangle((132, 606, width - 420, 644), radius=19, fill="#6B7280")
    draw.ellipse((width - 262, 168, width - 132, 298), fill="#0A0A0A")
    out = io.BytesIO()
    image.save(out, format="PNG", optimize=True)
    return out.getvalue()


def chapter_xhtml(chapter_id: str, title: str, paragraphs: list[str], illustration: bool = False) -> str:
    body_parts = [f"    <p>{text}</p>" for text in paragraphs]
    if illustration:
        body_parts.insert(
            1,
            '    <figure><img src="illustration.png" alt="A picture post: a blue plate over two lines of text"/>'
            "</figure>",
        )
    body = "\n".join(body_parts)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
  <head>
    <meta charset="utf-8"/>
    <title>{title}</title>
    <link rel="stylesheet" type="text/css" href="style.css"/>
  </head>
  <body>
    <section epub:type="chapter" xmlns:epub="http://www.idpf.org/2007/ops">
    <h2>{title}</h2>
{body}
    </section>
  </body>
</html>
"""


def nav_xhtml() -> str:
    items = "\n".join(
        f'      <li><a href="chapter{i + 1}.xhtml">{title}</a></li>'
        for i, (title, _) in enumerate(CHAPTERS)
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
  <head><title>{TITLE}</title><meta charset="utf-8"/></head>
  <body>
    <nav epub:type="toc" id="toc">
      <h1>Contents</h1>
      <ol>
{items}
      </ol>
    </nav>
  </body>
</html>
"""


def ncx() -> str:
    points = "\n".join(
        f"""    <navPoint id="nav{i + 1}" playOrder="{i + 1}">
      <navLabel><text>{title}</text></navLabel>
      <content src="chapter{i + 1}.xhtml"/>
    </navPoint>"""
        for i, (title, _) in enumerate(CHAPTERS)
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="urn:uuid:epub-feed-basics"/>
    <meta name="dtb:depth" content="1"/>
  </head>
  <docTitle><text>{TITLE}</text></docTitle>
  <navMap>
{points}
  </navMap>
</ncx>
"""


def content_opf() -> str:
    chapter_items = "\n".join(
        f'    <item id="chapter{i + 1}" href="chapter{i + 1}.xhtml" media-type="application/xhtml+xml"/>'
        for i in range(len(CHAPTERS))
    )
    spine = "\n".join(f'    <itemref idref="chapter{i + 1}"/>' for i in range(len(CHAPTERS)))
    return f"""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">urn:uuid:epub-feed-basics</dc:identifier>
    <dc:title>{TITLE}</dc:title>
    <dc:creator>{AUTHOR}</dc:creator>
    <dc:publisher>{PUBLISHER}</dc:publisher>
    <dc:date>{DATE}</dc:date>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">{DATE}T00:00:00Z</meta>
    <meta name="cover" content="cover-image"/>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="style.css" media-type="text/css"/>
{chapter_items}
    <item id="cover-image" href="cover.png" media-type="image/png" properties="cover-image"/>
    <item id="illustration" href="illustration.png" media-type="image/png"/>
  </manifest>
  <spine toc="ncx">
{spine}
  </spine>
</package>
"""


CONTAINER = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

STYLE = """body { margin: 0; padding: 0; }
h2 { font-size: 1.4em; margin: 1.2em 0 0.6em; }
p { line-height: 1.5; margin: 0 0 1em; }
"""


def main() -> None:
    os.makedirs(os.path.dirname(ASSET), exist_ok=True)
    with zipfile.ZipFile(ASSET, "w") as epub:
        # The mimetype entry must be first and stored without compression.
        epub.writestr(
            zipfile.ZipInfo("mimetype"),
            "application/epub+zip",
            compress_type=zipfile.ZIP_STORED,
        )
        epub.writestr("META-INF/container.xml", CONTAINER, compress_type=zipfile.ZIP_DEFLATED)
        epub.writestr("OEBPS/content.opf", content_opf(), compress_type=zipfile.ZIP_DEFLATED)
        epub.writestr("OEBPS/nav.xhtml", nav_xhtml(), compress_type=zipfile.ZIP_DEFLATED)
        epub.writestr("OEBPS/toc.ncx", ncx(), compress_type=zipfile.ZIP_DEFLATED)
        epub.writestr("OEBPS/style.css", STYLE, compress_type=zipfile.ZIP_DEFLATED)
        epub.writestr("OEBPS/cover.png", cover_png(), compress_type=zipfile.ZIP_DEFLATED)
        epub.writestr("OEBPS/illustration.png", illustration_png(), compress_type=zipfile.ZIP_DEFLATED)
        for index, (title, paragraphs) in enumerate(CHAPTERS):
            epub.writestr(
                f"OEBPS/chapter{index + 1}.xhtml",
                chapter_xhtml(
                    f"chapter{index + 1}",
                    title,
                    paragraphs,
                    illustration=index == ILLUSTRATED_CHAPTER,
                ),
                compress_type=zipfile.ZIP_DEFLATED,
            )
    print(f"{ASSET} ({os.path.getsize(ASSET)} bytes, {len(CHAPTERS)} chapters)")


if __name__ == "__main__":
    main()
