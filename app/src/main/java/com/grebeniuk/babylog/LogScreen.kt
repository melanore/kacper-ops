package com.grebeniuk.babylog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.TextButton
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
 * Logging flow after step 1 (type picked on the home screen):
 *   Feed  → amount (ml) → time
 *   other → time
 * Back always goes one step back. Everything you touch sits in the bottom half (one-handed use).
 */
@Composable
fun LogScreen(
    c: AppController,
    initialType: String,
    onClose: () -> Unit,
    startCustom: Boolean = false,
    startOnTime: Boolean = false,
) {
    var type by remember { mutableStateOf(initialType) }
    val lastMl = remember { c.db.liveEvents().firstOrNull { it.isFeed && it.ml != null }?.ml }
    var ml by remember { mutableStateOf(if (startOnTime) lastMl else null) }
    var amountDone by remember { mutableStateOf(startOnTime) }
    val onAmount = type == Kind.FEED && !amountDone

    fun back() { if (type == Kind.FEED && amountDone) amountDone = false else onClose() }
    BackHandler { back() }

    AnimatedContent(
        targetState = onAmount,
        transitionSpec = {
            if (targetState) (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            else (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
        },
        label = "logStep",
    ) { amountStep ->
        if (amountStep) {
            AmountStep(lastMl, onBack = { back() }) { picked -> ml = picked; amountDone = true }
        } else {
            TimeStep(
                c, type, ml, startCustom,
                onBack = { back() },
                onType = { k -> type = k },
                onEditAmount = { amountDone = false },
                onDone = onClose,
            )
        }
    }
}

@Composable
private fun StepScaffold(
    type: String,
    title: String,
    onBack: () -> Unit,
    onTitle: (() -> Unit)? = null,
    scrollKey: Any? = null,
    bottom: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cat = LocalCats.current.of(type)
    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Surface(
                onClick = { onTitle?.invoke() }, enabled = onTitle != null,
                shape = RoundedCornerShape(50), color = cs.background,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(cat.container), contentAlignment = Alignment.Center) {
                        KindIcon(type, cat.accent, 24.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val scroll = rememberScrollState()
            // keep the thumb-zone controls in view when the panel grows
            LaunchedEffect(scrollKey) { scroll.animateScrollTo(scroll.maxValue) }
            Column(Modifier.verticalScroll(scroll).padding(top = 8.dp, bottom = 14.dp), content = content)
        }
        bottom()
        Spacer(Modifier.height(12.dp))
    }
}

/** Feed step 2: how many ml. A preset tap goes straight on to the time step. */
@Composable
private fun AmountStep(lastMl: Int?, onBack: () -> Unit, onPicked: (Int?) -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    var ml by remember { mutableStateOf(lastMl ?: 40) }

    StepScaffold(
        type = Kind.FEED, title = s["feed"], onBack = onBack,
        bottom = {
            Button(
                onClick = { view.tick(); onPicked(ml) },
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(80.dp),
            ) { Text("${s["next"]} · $ml ${s["ml"]}", fontSize = 24.sp, fontWeight = FontWeight.Bold, style = Tnum) }
        },
    ) {
        Surface(shape = RoundedCornerShape(30.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s["howMuch"].uppercase(), style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant, letterSpacing = 1.sp)
                Text("$ml", fontSize = 84.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp, style = Tnum)
                Text(
                    s["ml"] + (lastMl?.let { "  ·  ${s["lastAmount"]}: $it ${s["ml"]}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AmountPicker(
            ml, height = 76,
            onChange = { view.tick(); ml = it },
            onPreset = { view.tick(); ml = it; onPicked(it) },
        )
        TextButton(onClick = { onPicked(null) }, modifier = Modifier.align(Alignment.End).padding(top = 4.dp)) {
            Text(s["noAmount"])
        }
    }
}

/** Step "when": defaults to now; big ±10/±15 nudges; optional full picker. */
@Composable
private fun TimeStep(
    c: AppController,
    type: String,
    ml: Int?,
    startCustom: Boolean,
    onBack: () -> Unit,
    onType: (String) -> Unit,
    onEditAmount: () -> Unit,
    onDone: () -> Unit,
) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val now = c.now

    var t by remember { mutableLongStateOf(floor10(System.currentTimeMillis())) }
    var followNow by remember { mutableStateOf(true) } // untouched → keep tracking the clock
    var custom by remember { mutableStateOf(startCustom) }
    LaunchedEffect(now) { if (followNow) t = floor10(now) }

    val cap = floor10(maxOf(now, System.currentTimeMillis()))
    val isNow = t >= cap
    fun setT(v: Long) {
        val capNow = floor10(System.currentTimeMillis())
        t = minOf(v, capNow)
        followNow = t >= capNow
        view.tick()
    }

    val isFeed = type == Kind.FEED
    val title = if (isFeed && ml != null) "${s["feed"]} · $ml ${s["ml"]}" else s[type]

    StepScaffold(
        type = type, title = title, onBack = onBack,
        onTitle = if (isFeed) onEditAmount else null,
        scrollKey = custom to type,
        bottom = {
            Button(
                onClick = {
                    view.tick()
                    c.log(type, t, side = if (isFeed && ml != null) "bottle" else null, ml = if (isFeed) ml else null)
                    onDone()
                },
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(80.dp),
            ) { Text("${s["logAt"]} ${hhmm(t)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, style = Tnum) }
        },
    ) {
        // change type without going back (picking Feed here opens the amount step)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Kind.all.forEach { k ->
                val kc = LocalCats.current.of(k)
                Surface(
                    onClick = { if (type != k) { view.tick(); onType(k) } },
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
    }
}
