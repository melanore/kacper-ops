package com.grebeniuk.babylog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Step 2 of logging: confirm *when*. Defaults to now; big ±10/±15 nudges; optional full picker.
 * Everything you touch sits in the bottom half so it works one-handed.
 */
@Composable
fun LogScreen(c: AppController, initialType: String, onClose: () -> Unit, startCustom: Boolean = false) {
    var type by remember { mutableStateOf(initialType) }
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val cat = LocalCats.current.of(type)
    val view = LocalView.current
    val now = c.now

    var t by remember { mutableLongStateOf(floor10(System.currentTimeMillis())) }
    var followNow by remember { mutableStateOf(true) } // untouched → keep tracking the clock
    var custom by remember { mutableStateOf(startCustom) }
    var side by remember { mutableStateOf<String?>(null) }
    var ml by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(now) { if (followNow) t = floor10(now) }

    val cap = floor10(maxOf(now, System.currentTimeMillis()))
    val isNow = t >= cap
    fun setT(v: Long) {
        val capNow = floor10(System.currentTimeMillis())
        t = minOf(v, capNow)
        followNow = t >= capNow
        view.tick()
    }

    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        // ---- header (out of thumb reach on purpose: only "close" lives here, and Back does the same)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s["cancel"]) }
            Box(Modifier.size(40.dp).clip(CircleShape).background(cat.container), contentAlignment = Alignment.Center) {
                KindIcon(type, cat.accent, 24.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(s[type], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // ---- everything else is pushed to the bottom
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Kind.all.forEach { k ->
                        val kc = LocalCats.current.of(k)
                        Surface(
                            onClick = { if (type != k) { type = k; view.tick() } },
                            shape = RoundedCornerShape(18.dp),
                            color = if (type == k) kc.container else cs.surfaceContainerLow,
                            border = if (type == k) BorderStroke(2.dp, kc.accent) else null,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                KindIcon(k, if (type == k) kc.accent else cs.onSurfaceVariant, if (k == Kind.BOTH) 20.dp else 26.dp)
                            }
                        }
                    }
                }
                Surface(
                    onClick = { custom = !custom },
                    shape = RoundedCornerShape(30.dp),
                    color = cs.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s["when"].uppercase(), style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant, letterSpacing = 1.sp)
                        AnimatedContent(
                            targetState = hhmm(t),
                            transitionSpec = {
                                (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                            },
                            label = "time",
                        ) { txt ->
                            Text(txt, fontSize = 84.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp, style = Tnum)
                        }
                        Text(
                            if (isNow) "${s["now"]} · ${dayLabel(t, now, s)}" else "${dayLabel(t, now, s)} · ${agoText(now - t, s)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isNow) cs.primary else cs.onSurfaceVariant,
                        )
                    }
                }

                AnimatedVisibility(custom, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 12.dp)) { TimeGrid(t, now) { setT(it) } }
                }

                Spacer(Modifier.height(12.dp))
                Nudges(t < cap, height = 72) { d -> setT(t + d * MIN) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigOption(s["now"], isNow, { setT(cap) }, Modifier.weight(1f), height = 60)
                    BigOption(s["customTime"], custom, { custom = !custom }, Modifier.weight(1f), height = 60,
                        icon = { Icon(Icons.Outlined.Schedule, null) })
                }

                if (type == Kind.FEED) {
                    SectionTitle(s["feedDetails"])
                    SideOptions(side) { v ->
                        view.tick()
                        side = if (side == v) null else v
                    }
                    if (side == "bottle") MlOptions(ml) { m -> view.tick(); ml = m }
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        // ---- the one big action, right under the thumb
        Button(
            onClick = { view.tick(); c.log(type, t, side, ml); onClose() },
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().height(80.dp),
        ) {
            Text("${s["logAt"]} ${hhmm(t)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, style = Tnum)
        }
        Spacer(Modifier.height(12.dp))
    }
}
