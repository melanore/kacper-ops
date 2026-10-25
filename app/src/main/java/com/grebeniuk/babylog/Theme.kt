package com.grebeniuk.babylog

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fixed category colours (pee/poop/feed) that sit on top of the Material You scheme. */
@Immutable
data class Cat(val accent: Color, val container: Color)

@Immutable
data class Cats(val pee: Cat, val poop: Cat, val both: Cat, val feed: Cat) {
    fun of(type: String) = when (type) {
        Kind.PEE -> pee
        Kind.POOP -> poop
        Kind.BOTH -> both
        else -> feed
    }
}

private val LightCats = Cats(
    pee = Cat(Color(0xFFB98900), Color(0xFFFFF1C2)),
    poop = Cat(Color(0xFF85552F), Color(0xFFF3E3D3)),
    both = Cat(Color(0xFFA06B12), Color(0xFFF8E6C4)),
    feed = Cat(Color(0xFF2F6FD6), Color(0xFFDDE8FD)),
)
private val DarkCats = Cats(
    pee = Cat(Color(0xFFF5CD5A), Color(0xFF3B3220)),
    poop = Cat(Color(0xFFDDA67A), Color(0xFF3B2C21)),
    both = Cat(Color(0xFFE8BA66), Color(0xFF3C3121)),
    feed = Cat(Color(0xFF93B6FF), Color(0xFF1D2A45)),
)

val LocalCats = staticCompositionLocalOf { LightCats }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3E6FD8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E3FF), onPrimaryContainer = Color(0xFF0D2557),
    background = Color(0xFFFBF8F3), surface = Color(0xFFFBF8F3),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F2EB),
    surfaceContainer = Color(0xFFF0ECE5), surfaceContainerHigh = Color(0xFFEAE6DF),
    surfaceContainerHighest = Color(0xFFE4E0D9),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA9C2FF), onPrimary = Color(0xFF0D2557),
    primaryContainer = Color(0xFF26457F), onPrimaryContainer = Color(0xFFD9E3FF),
    background = Color(0xFF121316), surface = Color(0xFF121316),
    surfaceContainerLowest = Color(0xFF0D0E11), surfaceContainerLow = Color(0xFF1A1B1F),
    surfaceContainer = Color(0xFF1E1F23), surfaceContainerHigh = Color(0xFF282A2E),
    surfaceContainerHighest = Color(0xFF333539),
)

val Tnum = TextStyle(fontFeatureSettings = "tnum")

private val AppType = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AppTheme(dark: Boolean, dynamic: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalCats provides if (dark) DarkCats else LightCats) {
        MaterialTheme(colorScheme = scheme, typography = AppType, content = content)
    }
}
