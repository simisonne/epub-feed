package com.epubfeed.domain.model

enum class ThemeMode { LIGHTS_OUT, DIM, LIGHT }

enum class ReadingFont { SANS, SERIF }

enum class ReadingTextSize(val sp: Int) {
    SMALL(15),
    MEDIUM(17),
    LARGE(20),
}

/** The order the Likes and Saved tabs read in. */
enum class CollectionOrder {
    /** The post nearest the start of the book comes first, the way the feed reads. */
    FIRST_TO_LAST,

    /** The furthest read post comes first. */
    LAST_TO_FIRST,
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHTS_OUT,
    val readingFont: ReadingFont = ReadingFont.SANS,
    val textSize: ReadingTextSize = ReadingTextSize.MEDIUM,
    val postLength: PostLength = PostLength.MEDIUM,
    val collectionOrder: CollectionOrder = CollectionOrder.FIRST_TO_LAST,
    /** How the word list reads. Alphabetical by default: a word list is looked things up in. */
    val wordSort: WordSort = WordSort.ALPHABETICAL,
    /**
     * Which installed dictionary answers first. With English and German on the device this is what
     * stops a German word from being answered by the English dictionary.
     */
    val dictionaryLanguage: String = "en",
)
