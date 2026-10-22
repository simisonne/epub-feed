package com.epubfeed.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.epubfeed.domain.model.PostLength
import com.epubfeed.domain.model.ReadingFont
import com.epubfeed.domain.model.ReadingTextSize
import com.epubfeed.domain.model.ThemeMode
import com.epubfeed.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "epub_feed_settings")

@Singleton
class SettingsRepository @Inject constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val FONT = stringPreferencesKey("font")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val POST_LENGTH = stringPreferencesKey("post_length")
        val LAST_BOOK = stringPreferencesKey("last_book")
        val SEED_DONE = intPreferencesKey("seed_done")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME].toEnumOrDefault(ThemeMode.LIGHTS_OUT),
            readingFont = prefs[Keys.FONT].toEnumOrDefault(ReadingFont.SANS),
            textSize = prefs[Keys.TEXT_SIZE].toEnumOrDefault(ReadingTextSize.MEDIUM),
            postLength = PostLength.fromName(prefs[Keys.POST_LENGTH]),
        )
    }

    val lastBookId: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_BOOK] }

    suspend fun currentSettings(): UserSettings = settings.first()

    suspend fun currentLastBookId(): String? = lastBookId.first()

    suspend fun setTheme(mode: ThemeMode) = putString(Keys.THEME, mode.name)

    suspend fun setFont(font: ReadingFont) = putString(Keys.FONT, font.name)

    suspend fun setTextSize(size: ReadingTextSize) = putString(Keys.TEXT_SIZE, size.name)

    suspend fun setPostLength(length: PostLength) = putString(Keys.POST_LENGTH, length.name)

    suspend fun setLastBookId(bookId: String?) {
        context.dataStore.edit { prefs ->
            if (bookId == null) prefs.remove(Keys.LAST_BOOK) else prefs[Keys.LAST_BOOK] = bookId
        }
    }

    suspend fun setSeedDone() {
        context.dataStore.edit { it[Keys.SEED_DONE] = 1 }
    }

    suspend fun isSeedDone(): Boolean = context.dataStore.data.map { it[Keys.SEED_DONE] == 1 }.first()

    private suspend fun putString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback
}
