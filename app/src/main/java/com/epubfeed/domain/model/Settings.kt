package com.epubfeed.domain.model

enum class ThemeMode { LIGHTS_OUT, DIM, LIGHT }

enum class ReadingFont { SANS, SERIF }

enum class ReadingTextSize(val sp: Int) {
    SMALL(15),
    MEDIUM(17),
    LARGE(20),
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHTS_OUT,
    val readingFont: ReadingFont = ReadingFont.SANS,
    val textSize: ReadingTextSize = ReadingTextSize.MEDIUM,
    val postLength: PostLength = PostLength.MEDIUM,
)
