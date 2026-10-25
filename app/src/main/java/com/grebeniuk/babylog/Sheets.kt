package com.grebeniuk.babylog

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

val ML_PRESETS = listOf(30, 40, 50, 60)
const val ML_STEP = 5
const val ML_MAX = 90

fun sideLabel(side: String?, s: Strings): String? = when (side) {
    "L" -> s["left"]
    "R" -> s["right"]
    "LR" -> s["bothSides"]
    "bottle" -> s["bottle"]
    else -> null
}

/** −15 / −10 / +10 / +15 minute nudges. */
@Composable
fun Nudges(canForward: Boolean, height: Int = 52, onShift: (Int) -> Unit) {
    val s = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(-15, -10, 10, 15).forEach { d ->
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

/** 30/40/50/60 presets + −5/+5 stepper (5…90 ml). [onPreset] lets the log flow jump ahead on a preset tap. */
@Composable
fun AmountPicker(ml: Int?, onChange: (Int) -> Unit, height: Int = 64, onPreset: (Int) -> Unit = onChange) {
    val s = LocalStrings.current
    val base = ml ?: 40
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ML_PRESETS.forEach { m ->
                Pill("$m", selected = ml == m, onClick = { onPreset(m) }, sub = s["ml"],
                    fontSize = if (height >= 72) 26 else 20, modifier = Modifier.weight(1f).height(height.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("\u2212$ML_STEP", selected = false, enabled = base > ML_STEP,
                onClick = { onChange((base - ML_STEP).coerceIn(ML_STEP, ML_MAX)) },
                sub = s["ml"], fontSize = 22, modifier = Modifier.weight(1f).height(height.dp))
            Text(
                if (ml != null) "$ml ${s["ml"]}" else "\u2014",
                fontSize = 30.sp, fontWeight = FontWeight.Black, style = Tnum, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.3f),
            )
            Pill("+$ML_STEP", selected = false, enabled = base < ML_MAX,
                onClick = { onChange((base + ML_STEP).coerceIn(ML_STEP, ML_MAX)) },
                sub = s["ml"], fontSize = 22, modifier = Modifier.weight(1f).height(height.dp))
        }
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
    val now = c.now
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit = {}) { scope.launch { state.hide() }.invokeOnCompletion { onDone(); after() } }
    val cap = floor10(System.currentTimeMillis())

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(s["edit"], style = MaterialTheme.typography.titleLarge)

            SectionTitle(s["type"])
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kind.all.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t ->
                            BigOption(s[t], e.type == t, {
                                e = if (t == Kind.FEED) e.copy(type = t) else e.copy(type = t, side = null, ml = null)
                            }, Modifier.weight(1f), kind = t)
                        }
                    }
                }
            }

            SectionTitle("${s["time"]} · ${hhmm(e.t)}")
            Nudges(e.t < cap) { d -> e = e.copy(t = minOf(e.t + d * MIN, cap)) }
            Spacer(Modifier.height(10.dp))
            TimeGrid(e.t, now) { v -> e = e.copy(t = v) }

            if (e.type == Kind.FEED) {
                SectionTitle(s["feed"])
                AmountPicker(e.ml, onChange = { m -> e = e.copy(side = "bottle", ml = m) })
                Spacer(Modifier.height(10.dp))
                SideOptions(e.side) { side -> e = e.copy(side = side, ml = if (side == "bottle") e.ml else null) }
            }

            SectionTitle(s["note"])
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                placeholder = { Text(s["notePh"]) },
                shape = RoundedCornerShape(16.dp), maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(s["by"])
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                c.db.liveProfiles().forEach { p ->
                    val n = c.profileName(p, s)
                    Pill(n, selected = e.by == p.id, onClick = { e = e.copy(by = p.id) }, fontSize = 15,
                        modifier = Modifier.height(48.dp), leading = { Avatar(p, n, 26.dp) })
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { val d = orig; c.delete(d.id); close { onDeleted(d) } },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.error),
                ) {
                    Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text(s["del"])
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { close() }) { Text(s["cancel"]) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    c.update(e.copy(note = note.trim().ifEmpty { null }))
                    close()
                }, modifier = Modifier.height(52.dp)) { Text(s["save"], fontWeight = FontWeight.Bold) }
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
