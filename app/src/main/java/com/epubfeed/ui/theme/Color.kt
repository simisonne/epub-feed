package com.epubfeed.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The handful of colours the X style feed needs, per theme. Material 3 supplies the
 * rest; these drive the details Material does not model (thread lines, action tints).
 */
@Immutable
data class XColors(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val text: Color,
    val muted: Color,
    val divider: Color,
    val threadLine: Color,
    val accent: Color,
    val like: Color,
    val onAccent: Color,
)

val LightsOutColors = XColors(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    elevated = Color(0xFF16181C),
    text = Color(0xFFE7E9EA),
    muted = Color(0xFF71767B),
    divider = Color(0xFF2F3336),
    threadLine = Color(0xFF333639),
    accent = Color(0xFF1D9BF0),
    like = Color(0xFFF91880),
    onAccent = Color(0xFFFFFFFF),
)

val DimColors = XColors(
    background = Color(0xFF15202B),
    surface = Color(0xFF15202B),
    elevated = Color(0xFF1E2732),
    text = Color(0xFFF7F9F9),
    muted = Color(0xFF8B98A5),
    divider = Color(0xFF38444D),
    threadLine = Color(0xFF3E4A55),
    accent = Color(0xFF1D9BF0),
    like = Color(0xFFF91880),
    onAccent = Color(0xFFFFFFFF),
)

val LightColors = XColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFF7F9F9),
    text = Color(0xFF0F1419),
    muted = Color(0xFF536471),
    divider = Color(0xFFEFF3F4),
    threadLine = Color(0xFFCFD9DE),
    accent = Color(0xFF1D9BF0),
    like = Color(0xFFF91880),
    onAccent = Color(0xFFFFFFFF),
)

val LocalXColors = staticCompositionLocalOf { LightsOutColors }
