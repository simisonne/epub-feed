package com.epubfeed.ui.import

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.epubfeed.data.repo.ImportState
import com.epubfeed.ui.theme.LocalXColors

/** Blocking progress while a book is parsed, plus the friendly error dialog. */
@Composable
fun ImportOverlay(state: ImportState, onDismiss: () -> Unit) {
    val x = LocalXColors.current
    when (state) {
        is ImportState.Working -> {
            Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            )) {
                Surface(shape = RoundedCornerShape(20.dp), color = x.elevated) {
                    Column(Modifier.padding(24.dp).fillMaxWidth()) {
                        CircularProgressIndicator(color = x.accent, strokeWidth = 2.dp, modifier = Modifier.height(28.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.step,
                            color = x.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.fraction != null) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { state.fraction.coerceIn(0f, 1f) },
                                color = x.accent,
                                trackColor = x.divider,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        is ImportState.Failure -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = x.elevated,
                titleContentColor = x.text,
                textContentColor = x.muted,
                title = { Text("Can't open that file", fontWeight = FontWeight.Bold) },
                text = { Text(state.message, lineHeight = 20.sp) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = x.accent, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
        else -> Unit
    }
}
