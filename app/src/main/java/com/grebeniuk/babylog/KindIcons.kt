package com.grebeniuk.babylog

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Hand-drawn 24×24 glyphs for the three things we log (no emoji anywhere in the UI). */
object KindIcons {
    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t)
        arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad)
        arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b)
        arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad)
        arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply { path(fill = SolidColor(Color.Black), pathBuilder = block) }
            .build()

    val Drop: ImageVector by lazy {
        icon("drop") {
            moveTo(12f, 2.5f)
            curveTo(12f, 2.5f, 5.5f, 10f, 5.5f, 14.5f)
            arcTo(6.5f, 6.5f, 0f, false, false, 18.5f, 14.5f)
            curveTo(18.5f, 10f, 12f, 2.5f, 12f, 2.5f)
            close()
        }
    }

    val Pile: ImageVector by lazy {
        icon("pile") {
            roundRect(3f, 15.2f, 21f, 21.2f, 3f)
            roundRect(5.5f, 10.6f, 18.5f, 16.2f, 2.8f)
            roundRect(8f, 6.4f, 16f, 11.6f, 2.6f)
            moveTo(12.6f, 2.6f)
            curveTo(14.2f, 3.4f, 14.6f, 5.2f, 13.9f, 7f)
            lineTo(10.4f, 7f)
            curveTo(10.4f, 5.1f, 11.2f, 3.6f, 12.6f, 2.6f)
            close()
        }
    }

    val Bottle: ImageVector by lazy {
        icon("bottle") {
            moveTo(10.2f, 4.8f)
            curveTo(10.2f, 2.2f, 13.8f, 2.2f, 13.8f, 4.8f)
            lineTo(14.2f, 5.6f)
            lineTo(9.8f, 5.6f)
            close()
            roundRect(7.6f, 5.4f, 16.4f, 8f, 1.1f)
            roundRect(8.3f, 8.4f, 15.7f, 21.6f, 2.6f)
        }
    }
}

/** Icon(s) for an entry type, tinted with [tint]. "Both" shows drop + pile side by side. */
@Composable
fun KindIcon(type: String, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    when (type) {
        Kind.PEE -> Icon(KindIcons.Drop, null, tint = tint, modifier = modifier.size(size))
        Kind.POOP -> Icon(KindIcons.Pile, null, tint = tint, modifier = modifier.size(size))
        Kind.FEED -> Icon(KindIcons.Bottle, null, tint = tint, modifier = modifier.size(size))
        else -> Row(modifier) {
            Icon(KindIcons.Drop, null, tint = tint, modifier = Modifier.size(size * 0.8f))
            Icon(KindIcons.Pile, null, tint = tint, modifier = Modifier.size(size * 0.8f))
        }
    }
}
