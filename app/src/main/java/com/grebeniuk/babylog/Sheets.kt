package com.grebeniuk.babylog

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ML_PRESETS = listOf(10, 20, 30, 40, 50, 60, 80, 100, 120, 150)

fun sideLabel(side: String?, s: Strings): String? = when (side) {
    "L" -> s["left"]
    "R" -> s["right"]
    "LR" -> s["bothSides"]
    "bottle" -> s["bottle"]
    else -> null
}

@Composable
private fun SideOptions(selected: String?, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigOption(s["left"], selected == "L", { onPick("L") }, Modifier.weight(1f))
            BigOption(s["right"], selected == "R", { onPick("R") }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigOption(s["bothSides"], selected == "LR", { onPick("LR") }, Modifier.weight(1f))
            BigOption(s["bottle"], selected == "bottle", { onPick("bottle") }, Modifier.weight(1f), emoji = "🍼")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MlOptions(selected: Int?, onPick: (Int) -> Unit) {
    val s = LocalStrings.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        ML_PRESETS.forEach { m ->
            Pill("$m", selected = selected == m, onClick = { onPick(m) }, sub = s["ml"],
                modifier = Modifier.size(width = 62.dp, height = 52.dp))
        }
    }
}

/** Shown right after tapping Feed. Everything is optional; it closes itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSheet(c: AppController, id: String, onDone: () -> Unit) {
    val s = LocalStrings.current
    val view = LocalView.current
    val e = c.db.events[id]
    if (e == null || e.deleted) { LaunchedEffect(id) { onDone() }; return }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDone() } }
    LaunchedEffect(id, e.side) { delay(20_000); close() }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("🍼 ${s["feed"]} · ${hhmm(e.t)}", style = MaterialTheme.typography.titleLarge)
            Text(s["feedDetails"], color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            SideOptions(e.side) { side ->
                view.tick()
                c.update(e.copy(side = side, ml = if (side == "bottle") e.ml else null))
                if (side != "bottle") close()
            }
            AnimatedVisibility(e.side == "bottle") {
                MlOptions(e.ml) { m -> view.tick(); c.update(e.copy(ml = m)); close() }
            }
            TextButton(onClick = { close() }, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                Text(if (e.side == null) s["skip"] else s["done"])
            }
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
    val nowCap = floor10(System.currentTimeMillis())

    ModalBottomSheet(onDismissRequest = onDone, sheetState = state) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            Text(s["edit"], style = MaterialTheme.typography.titleLarge)

            SectionTitle(s["type"])
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.all.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            BigOption(s[t], e.type == t, {
                                e = if (t == Kind.FEED) e.copy(type = t) else e.copy(type = t, side = null, ml = null)
                            }, Modifier.weight(1f), emoji = Kind.emoji(t))
                        }
                    }
                }
            }

            SectionTitle("${s["time"]} · ${hhmm(e.t)}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { e = e.copy(t = plusDays(e.t, -1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null)
                }
                Text(dayLabel(e.t, now, s), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { e = e.copy(t = minOf(plusDays(e.t, 1), nowCap)) },
                    enabled = dayStart(e.t) < dayStart(now),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 24).chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { h ->
                            val ht = hourStart(withHour(e.t, h))
                            Pill(two(h), selected = hourOf(e.t) == h, enabled = ht <= now,
                                onClick = { e = e.copy(t = minOf(withHour(e.t, h), nowCap)) },
                                modifier = Modifier.weight(1f).height(44.dp), fontSize = 16)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    for (m in 0..50 step 10) {
                        val t = withMinute(e.t, m)
                        Pill(":" + two(m), selected = minuteOf(e.t) / 10 * 10 == m, enabled = t <= now,
                            onClick = { e = e.copy(t = t) }, modifier = Modifier.weight(1f).height(44.dp), fontSize = 16)
                    }
                }
            }

            if (e.type == Kind.FEED) {
                SectionTitle(s["feed"])
                SideOptions(e.side) { side -> e = e.copy(side = side, ml = if (side == "bottle") e.ml else null) }
                if (e.side == "bottle") MlOptions(e.ml) { m -> e = e.copy(ml = m) }
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
                        modifier = Modifier.height(44.dp), leading = { Avatar(p, n, 26.dp) })
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
                }) { Text(s["save"], fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoSheet(c: AppController, onDone: () -> Unit) {
    val s = LocalStrings.current
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s["loggingAs"], style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            c.db.liveProfiles().forEach { p ->
                val n = c.profileName(p, s)
                Pill(n, selected = c.cfg.me == p.id, onClick = { c.setMe(p.id); onDone() }, fontSize = 17,
                    modifier = Modifier.fillMaxWidth().height(60.dp), leading = { Avatar(p, n, 32.dp) })
            }
        }
    }
}
