package com.epubfeed.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.domain.model.PostLength
import com.epubfeed.domain.model.ReadingFont
import com.epubfeed.domain.model.ReadingTextSize
import com.epubfeed.domain.model.ThemeMode
import com.epubfeed.domain.model.UserSettings
import com.epubfeed.ui.components.SectionDivider
import com.epubfeed.ui.components.SegmentedOptions
import com.epubfeed.ui.components.SettingsRow
import com.epubfeed.ui.components.SettingsSectionLabel
import com.epubfeed.ui.theme.LocalXColors
import com.epubfeed.ui.theme.readingFontFamily

data class SettingsActions(
    val setTheme: (ThemeMode) -> Unit,
    val setFont: (ReadingFont) -> Unit,
    val setTextSize: (ReadingTextSize) -> Unit,
    val setPostLength: (PostLength) -> Unit,
)

/** The "Aa" sheet: type size, font, post length and theme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: UserSettings,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    val x = LocalXColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = x.background,
        contentColor = x.text,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = x.divider) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Reading settings",
                color = x.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            SettingsSectionLabel("Text size")
            SegmentedOptions(
                options = ReadingTextSize.entries.toList(),
                selected = settings.textSize,
                label = { size ->
                    when (size) {
                        ReadingTextSize.SMALL -> "S"
                        ReadingTextSize.MEDIUM -> "M"
                        ReadingTextSize.LARGE -> "L"
                    }
                },
                onSelect = actions.setTextSize,
            )

            SettingsRow(title = "Font") {
                SegmentedOptions(
                    options = ReadingFont.entries.toList(),
                    selected = settings.readingFont,
                    label = { if (it == ReadingFont.SANS) "Sans" else "Serif" },
                    onSelect = actions.setFont,
                )
            }

            SectionDivider()

            SettingsSectionLabel("Post length")
            SegmentedOptions(
                options = PostLength.entries.toList(),
                selected = settings.postLength,
                label = { length ->
                    when (length) {
                        PostLength.SHORT -> "Short"
                        PostLength.MEDIUM -> "Medium"
                        PostLength.LONG -> "Long"
                    }
                },
                onSelect = actions.setPostLength,
            )
            SectionDivider()

            SettingsSectionLabel("Theme")
            SegmentedOptions(
                options = ThemeMode.entries.toList(),
                selected = settings.themeMode,
                label = { mode ->
                    when (mode) {
                        ThemeMode.LIGHTS_OUT -> "Lights out"
                        ThemeMode.DIM -> "Dim"
                        ThemeMode.LIGHT -> "Light"
                    }
                },
                onSelect = actions.setTheme,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Preview",
                color = x.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
                color = x.text,
                fontSize = settings.textSize.sp.sp,
                lineHeight = settings.textSize.sp.sp * 1.38f,
                fontFamily = readingFontFamily(settings.readingFont),
            )
        }
    }
}
