package com.grebeniuk.babylog

import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

const val ML_DEFAULT = 60
const val ML_MIN = 5
const val ML_MAX = 90

fun sideLabel(side: String?, s: Strings): String? = when (side) {
    "L" -> s["left"]
    "R" -> s["right"]
    "LR" -> s["bothSides"]
    "bottle" -> s["bottle"]
    else -> null
}

/** −10 / −5 / +5 / +10 minute nudges. */
@Composable
fun Nudges(canForward: Boolean, height: Int = 52, steps: List<Int> = listOf(-10, -5, 5, 10), onShift: (Int) -> Unit) {
    val s = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEach { d ->
            Pill(
                if (d < 0) "−${-d}" else "+$d",
                selected = false,
                enabled = d < 0 || canForward,
                onClick = { onShift(d) },
                sub = s["min"],
                fontSize = if (height >= 64) 24 else 18,
                modifier = Modifier.weight(1f).height(height.dp),
            )
        }
    }
}

/** Full custom time: day ‹ › , 24 hour buttons, 6 minute buttons. */
@Composable
fun TimeGrid(t: Long, now: Long, onChange: (Long) -> Unit) {
    val s = LocalStrings.current
    val cap = floor10(now)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange(plusDays(t, -1)) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
            }
            Text(dayLabel(t, now, s), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = { onChange(minOf(plusDays(t, 1), cap)) },
                enabled = dayStart(t) < dayStart(now),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
        }
        (0 until 24).chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { h ->
                    Pill(two(h), selected = hourOf(t) == h, enabled = hourStart(withHour(t, h)) <= now,
                        onClick = { onChange(minOf(withHour(t, h), cap)) },
                        modifier = Modifier.weight(1f).height(52.dp), fontSize = 18)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in 0..50 step 10) {
                val v = withMinute(t, m)
                Pill(":" + two(m), selected = minuteOf(t) == m, enabled = v <= now,
                    onClick = { onChange(v) }, modifier = Modifier.weight(1f).height(52.dp), fontSize = 18)
            }
        }
    }
}

@Composable
fun SideOptions(selected: String?, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigOption(s["left"], selected == "L", { onPick("L") }, Modifier.weight(1f))
            BigOption(s["right"], selected == "R", { onPick("R") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigOption(s["bothSides"], selected == "LR", { onPick("LR") }, Modifier.weight(1f))
            BigOption(s["bottle"], selected == "bottle", { onPick("bottle") }, Modifier.weight(1f), kind = Kind.FEED)
        }
    }
}

/** −10 / −5 / +5 / +10 ml buttons (5…90 ml). Optional value shown in the middle. */
@Composable
fun AmountStepper(ml: Int, onChange: (Int) -> Unit, height: Int = 52, showValue: Boolean = false) {
    val s = LocalStrings.current
    @Composable
    fun Btn(d: Int, m: Modifier) {
        val target = (ml + d).coerceIn(ML_MIN, ML_MAX)
        Pill(
            if (d < 0) "\u2212${-d}" else "+$d",
            selected = false,
            enabled = target != ml,
            onClick = { onChange(target) },
            sub = s["ml"],
            fontSize = if (height >= 64) 24 else 18,
            modifier = m.height(height.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn(-10, Modifier.weight(1f))
        Btn(-5, Modifier.weight(1f))
        if (showValue) {
            Text("$ml ${s["ml"]}", fontSize = 22.sp, fontWeight = FontWeight.Black, style = Tnum,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1.3f))
        }
        Btn(5, Modifier.weight(1f))
        Btn(10, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditSheet(c: AppController, id: String, onDone: () -> Unit, onDeleted: (Event) -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val orig = c.db.events[id]
    if (orig == null) { LaunchedEffect(id) { onDone() }; return }
    var e by remember(id) { mutableStateOf(orig) }
    var note by remember(id) { mutableStateOf(orig.note.orEmpty()) }
    var pickTime by remember(id) { mutableStateOf(false) }
    val now = c.now
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) { scope.launch { state.hide() }.invokeOnCompletion { onDone(); after() } }
    val cap = floor10(System.currentTimeMillis())

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            // Content is compact enough to fit; it only scrolls if the hour grid is opened on a small phone.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // type: 4 icon toggles
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kind.all.forEach { k ->
                        val kc = LocalCats.current.of(k)
                        Surface(
                            onClick = {
                                e = if (k == Kind.FEED) e.copy(type = k, side = "bottle", ml = e.ml ?: c.lastFeedMl())
                                else e.copy(type = k, side = null, ml = null)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (e.type == k) kc.container else cs.surfaceContainerHigh,
                            border = if (e.type == k) BorderStroke(2.dp, kc.accent) else null,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                KindIcon(k, if (e.type == k) kc.accent else cs.onSurfaceVariant, if (k == Kind.BOTH) 18.dp else 24.dp)
                            }
                        }
                    }
                }

                // time: value + day, nudges, optional full grid
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hhmm(e.t), fontSize = 40.sp, fontWeight = FontWeight.Black, style = Tnum)
                    Spacer(Modifier.width(10.dp))
                    Text(dayLabel(e.t, now, s), style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Pill(s["customTime"], selected = pickTime, onClick = { pickTime = !pickTime }, fontSize = 15,
                        modifier = Modifier.height(44.dp), leading = { Icon(Icons.Outlined.Schedule, null) })
                }
                Nudges(e.t < cap, height = 52, steps = listOf(-10, -5, 5, 10)) { d -> e = e.copy(t = minOf(e.t + d * MIN, cap)) }
                if (pickTime) TimeGrid(e.t, now) { v -> e = e.copy(t = v) }

                // feed amount (bottle only)
                if (e.type == Kind.FEED) {
                    AmountStepper(e.ml ?: c.lastFeedMl(), onChange = { m -> e = e.copy(side = "bottle", ml = m) },
                        height = 52, showValue = true)
                }

                // who
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    c.db.liveProfiles().forEach { p ->
                        val n = c.profileName(p, s)
                        Pill(n, selected = e.by == p.id, onClick = { e = e.copy(by = p.id) }, fontSize = 14,
                            modifier = Modifier.weight(1f).height(44.dp), leading = { Avatar(p, n, 22.dp) })
                    }
                }

                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text(s["notePh"]) }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
            }

            // actions: always visible
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp)) {
                OutlinedButton(
                    onClick = { val d = orig; c.delete(d.id); close { onDeleted(d) } },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(4.dp)); Text(s["del"], maxLines = 1) }
                FilledTonalButton(onClick = { close() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(s["cancel"], maxLines = 1)
                }
                Button(onClick = {
                    c.update(e.copy(note = note.trim().ifEmpty { null }))
                    close()
                }, modifier = Modifier.weight(1f).height(56.dp)) { Text(s["save"], fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoSheet(c: AppController, onDone: () -> Unit) {
    val s = LocalStrings.current
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s["loggingAs"], style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            c.db.liveProfiles().forEach { p ->
                val n = c.profileName(p, s)
                Pill(n, selected = c.cfg.me == p.id, onClick = { c.setMe(p.id); onDone() }, fontSize = 18,
                    modifier = Modifier.fillMaxWidth().height(64.dp), leading = { Avatar(p, n, 34.dp) })
            }
        }
    }
}
