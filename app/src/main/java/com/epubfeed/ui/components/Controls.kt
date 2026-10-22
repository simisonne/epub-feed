package com.epubfeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubfeed.ui.theme.LocalXColors

/** A pill segmented control, the closest X style equivalent of a settings toggle row. */
@Composable
fun <T> SegmentedOptions(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalXColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(x.elevated)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) x.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) x.onAccent else x.muted,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    val x = LocalXColors.current
    Text(
        text = text,
        color = x.text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun SettingsRow(title: String, description: String? = null, content: @Composable () -> Unit) {
    val x = LocalXColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, color = x.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (description != null) {
            Spacer(Modifier.height(2.dp))
            Text(description, color = x.muted, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SectionDivider() {
    val x = LocalXColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(x.divider),
    )
}

@Composable
fun EmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val x = LocalXColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = x.muted,
            modifier = Modifier.width(44.dp).height(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = x.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = x.muted,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
