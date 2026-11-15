package com.epubfeed.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.epubfeed.data.thesaurus.LanguageStatus
import com.epubfeed.data.thesaurus.PackManager
import com.epubfeed.ui.components.SectionDivider
import com.epubfeed.ui.components.SegmentedOptions
import com.epubfeed.ui.components.SettingsRow
import com.epubfeed.ui.components.SettingsSectionLabel
import com.epubfeed.ui.theme.LocalXColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val packs: PackManager,
) : ViewModel() {

    val languages: StateFlow<List<LanguageStatus>> = packs.languages

    val networkBlocked: StateFlow<Boolean> = packs.networkBlocked

    fun download(language: String) {
        viewModelScope.launch {
            // A download that never started answers with a reason: an "Already downloading" or an
            // unknown language must land on the row, not disappear.
            packs.download(language).exceptionOrNull()?.let { failure ->
                packs.showError(language, failure.message ?: "Download failed")
            }
        }
    }

    fun showError(language: String, message: String) {
        packs.showError(language, message)
    }

    fun remove(language: String) {
        packs.remove(language)
    }

    fun clearError(language: String) {
        packs.clearError(language)
    }

    fun refresh() {
        packs.refresh()
    }
}

/**
 * The dictionary rows, one per language. Every pack is a file the reader downloads once and then
 * reads from offline; installing a second language is the same one decision, so it is the same row
 * rather than a switch to flip somewhere else.
 */
@Composable
fun DictionarySettings(
    dictionaryLanguage: String,
    onSelectLanguage: (String) -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val x = LocalXColors.current
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val networkBlocked by viewModel.networkBlocked.collectAsStateWithLifecycle()
    val installed = languages.filter { it.installed }

    // The rows are read from the files each time the sheet opens, so nothing shows a state that
    // was true before a pack was added or removed.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Downloading a pack is the only network call the app makes, so the permission is asked for
    // here and only here. Newer Android releases hand INTERNET out as a runtime permission; on the
    // older ones the check is already satisfied and no dialog appears.
    val context = LocalContext.current
    var awaitingPermission by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val language = awaitingPermission
        awaitingPermission = null
        when {
            language == null -> Unit
            granted -> viewModel.download(language)
            else -> viewModel.showError(
                language,
                "Allow network access for ePUB Feed in Android settings to download",
            )
        }
    }
    val startDownload: (String) -> Unit = { language ->
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            viewModel.download(language)
        } else {
            awaitingPermission = language
            permission.launch(Manifest.permission.INTERNET)
        }
    }

    SectionDivider()
    SettingsSectionLabel("Dictionary")

    languages.forEach { status ->
        LanguageRow(
            status = status,
            onDownload = { startDownload(status.spec.language) },
            onRemove = { viewModel.remove(status.spec.language) },
            onClearError = { viewModel.clearError(status.spec.language) },
        )
    }

    if (networkBlocked) {
        Text(
            text = "Android is blocking network access for ePUB Feed. Turn it on in the app's settings, then download again.",
            color = x.like,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
                .padding(vertical = 8.dp),
        )
    }

    if (installed.size > 1) {
        SettingsRow(
            title = "Lookup language",
            description = "Which dictionary answers first. Every installed one is still asked.",
        ) {
            SegmentedOptions(
                options = installed.map { it.spec.language },
                selected = dictionaryLanguage.takeIf { code -> installed.any { it.spec.language == code } }
                    ?: installed.first().spec.language,
                label = { code -> languages.firstOrNull { it.spec.language == code }?.spec?.label ?: code },
                onSelect = onSelectLanguage,
            )
        }
    }
}

@Composable
private fun LanguageRow(
    status: LanguageStatus,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onClearError: () -> Unit,
) {
    val x = LocalXColors.current
    val spec = status.spec

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = spec.label,
                color = x.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    status.downloading -> "Downloading, ${(status.progress * 100).toInt()}%"
                    status.installed -> "Installed, works without a connection"
                    else -> "${megabytes(spec.downloadBytes)} download, works without a connection"
                },
                color = x.muted,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            status.error?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = error,
                    color = x.like,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onClearError),
                )
            }
            if (status.downloading) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(x.divider, RoundedCornerShape(999.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(status.progress.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .background(x.accent, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            status.downloading -> Unit
            status.installed -> SmallAction(label = "Remove", filled = false, onClick = onRemove)
            else -> SmallAction(label = "Download", filled = true, onClick = onDownload)
        }
    }
}

@Composable
private fun SmallAction(label: String, filled: Boolean, onClick: () -> Unit) {
    val x = LocalXColors.current
    Row(
        modifier = Modifier
            .height(40.dp)
            .background(
                if (filled) x.accent else Color.Transparent,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else x.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "Unknown size" else "${(bytes / 1_000_000.0).toInt()} MB"
