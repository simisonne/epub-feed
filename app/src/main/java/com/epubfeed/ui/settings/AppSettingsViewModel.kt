package com.epubfeed.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubfeed.data.prefs.SettingsRepository
import com.epubfeed.domain.model.CollectionOrder
import com.epubfeed.domain.model.PostLength
import com.epubfeed.domain.model.ReadingFont
import com.epubfeed.domain.model.ReadingTextSize
import com.epubfeed.domain.model.ThemeMode
import com.epubfeed.domain.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns the preference surface so the theme can be applied above the navigation graph. */
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setTheme(mode) }
    }

    fun setFont(font: ReadingFont) {
        viewModelScope.launch { settingsRepository.setFont(font) }
    }

    fun setTextSize(size: ReadingTextSize) {
        viewModelScope.launch { settingsRepository.setTextSize(size) }
    }

    fun setPostLength(length: PostLength) {
        viewModelScope.launch { settingsRepository.setPostLength(length) }
    }

    fun setCollectionOrder(order: CollectionOrder) {
        viewModelScope.launch { settingsRepository.setCollectionOrder(order) }
    }
}
