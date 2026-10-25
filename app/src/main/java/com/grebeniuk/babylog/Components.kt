package com.grebeniuk.babylog

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun View.tick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    )
}

fun profileColor(p: Profile?): Color =
    runCatching { Color(android.graphics.Color.parseColor(p?.color ?: "#888888")) }.getOrDefault(Color.Gray)

@Composable
fun Avatar(p: Profile?, name: String, size: Dp = 28.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(profileColor(p)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.44f).sp,
        )
    }
}

/** Rounded selectable tile used for hours, minutes and options. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
    fontSize: Int = 17,
    leading: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceContainerHighest, label = "pillBg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, label = "pillFg")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = bg,
        contentColor = fg,
        modifier = modifier.alpha(if (enabled) 1f else 0.32f),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, style = Tnum, textAlign = TextAlign.Center, maxLines = 1)
                if (sub != null) {
                    Text(sub.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp,
                        color = fg.copy(alpha = 0.7f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun SyncDot(state: SyncState) {
    val color = when (state) {
        SyncState.OFF -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        SyncState.OK -> Color(0xFF2EAA62)
        SyncState.PENDING -> Color(0xFFE39B1B)
        SyncState.ERROR -> MaterialTheme.colorScheme.error
        SyncState.SYNCING -> MaterialTheme.colorScheme.primary
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(
        Modifier.size(9.dp).alpha(if (state == SyncState.SYNCING) a else 1f).clip(CircleShape).background(color)
    )
}

@Composable
fun BigOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) cs.primary else cs.surfaceContainerHigh, label = "optBg")
    val fg by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, label = "optFg")
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = bg, contentColor = fg,
        modifier = modifier.height(58.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
            if (emoji != null) { Text(emoji, fontSize = 20.sp); Spacer(Modifier.width(8.dp)) }
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
        }
    }
}
