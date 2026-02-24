package com.housework.tracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.housework.tracker.ui.theme.SuccessGreen

@Composable
fun CalendarDayCell(
    day: Int,
    intensity: Float, // 0f = 활동 없음, 1f = 최대 활동
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val primaryColor = MaterialTheme.colorScheme.primary

    val backgroundColor = when {
        isSelected -> primaryColor.copy(alpha = 0.15f)
        intensity > 0f -> SuccessGreen.copy(alpha = intensity * 0.5f)
        else -> Color.Transparent
    }

    val borderModifier = when {
        isToday -> Modifier.border(2.dp, primaryColor, shape)
        isSelected -> Modifier.border(1.dp, primaryColor, shape)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(borderModifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
