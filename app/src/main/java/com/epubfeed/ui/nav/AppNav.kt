package com.epubfeed.ui.nav

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.epubfeed.data.repo.ImportState
import com.epubfeed.ui.feed.FeedScreen
import com.epubfeed.ui.import.ImportOverlay
import com.epubfeed.ui.settings.AppSettingsViewModel
import com.epubfeed.ui.settings.SettingsActions
import com.epubfeed.ui.theme.EpubFeedTheme
import com.epubfeed.ui.theme.LocalXColors

/**
 * Single activity, one nav graph. "home" resolves the last opened book, "book/{bookId}"
 * opens a specific one (used after an import).
 */
@Composable
fun AppNav(
    importState: ImportState,
    onImportUri: (Uri) -> Unit,
    onDismissImport: () -> Unit,
    settingsViewModel: AppSettingsViewModel = hiltViewModel(),
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val actions = remember(settingsViewModel) {
        SettingsActions(
            setTheme = settingsViewModel::setTheme,
            setFont = settingsViewModel::setFont,
            setTextSize = settingsViewModel::setTextSize,
            setPostLength = settingsViewModel::setPostLength,
        )
    }

    EpubFeedTheme(themeMode = settings.themeMode) {
        val navController = rememberNavController()
        val x = LocalXColors.current

        LaunchedEffect(importState) {
            val success = importState as? ImportState.Success ?: return@LaunchedEffect
            navController.navigateToBook(success.bookId)
            onDismissImport()
        }

        Box(Modifier.fillMaxSize().background(x.background)) {
            NavHost(navController = navController, startDestination = HomeRoute) {
                composable(HomeRoute) {
                    FeedScreen(
                        bookId = null,
                        settings = settings,
                        settingsActions = actions,
                        onImportUri = onImportUri,
                    )
                }
                composable(
                    route = BookRoute,
                    arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                ) { entry ->
                    FeedScreen(
                        bookId = entry.arguments?.getString("bookId"),
                        settings = settings,
                        settingsActions = actions,
                        onImportUri = onImportUri,
                    )
                }
            }
            ImportOverlay(state = importState, onDismiss = onDismissImport)
        }
    }
}

private const val HomeRoute = "home"
private const val BookRoute = "book/{bookId}"

private fun NavHostController.navigateToBook(bookId: String) {
    navigate("book/$bookId") {
        popUpTo(HomeRoute) { inclusive = false }
        launchSingleTop = true
    }
}
