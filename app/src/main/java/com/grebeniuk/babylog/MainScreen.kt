package com.grebeniuk.babylog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Step 1: pick what happened (big buttons docked at the bottom). History and "time since" above. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(c: AppController, onSettings: () -> Unit, onPick: (String) -> Unit, onSummary: () -> Unit = {}) {
    val s = LocalStrings.current
    val view = LocalView.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editId by remember { mutableStateOf<String?>(null) }
    var whoOpen by remember { mutableStateOf(false) }
    var daysShown by remember { mutableIntStateOf(7) }

    val now = c.now
    val events = remember(c.db) { c.db.liveEvents() }
    val days = remember(events) { events.groupBy { dayStart(it.t) }.toList() }

    fun showUndo(msg: String, undo: () -> Unit) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            val r = snack.showSnackbar(msg, actionLabel = s["undo"], duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) { undo(); view.tick() }
        }
    }

    // coming back from step 2: offer Undo once
    val note = c.undoNote
    LaunchedEffect(note) {
        if (note != null && !note.shown) {
            note.shown = true
            val e = note.event
            showUndo("${s["logged"]}: ${s[e.type]} · ${hhmm(e.t)}") { c.delete(e.id) }
        }
    }

    Scaffold(
        topBar = { TopBar(c, onWho = { whoOpen = true }, onSettings = onSettings, onSummary = onSummary) },
        bottomBar = { TypePad { view.tick(); onPick(it) } },
        snackbarHost = { SnackbarHost(snack) { Snackbar(it, shape = RoundedCornerShape(16.dp)) } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "since") { SinceRow(events, now) }
            item(key = "histTitle") { SectionTitle(s["history"], Modifier.padding(top = 4.dp)) }
            if (events.isEmpty()) {
                item(key = "empty") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(s["noEntries"], textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp))
                    }
                }
            }
            days.take(daysShown).forEach { (day, items) ->
                stickyHeader(key = "h$day") { DayHeader(day, items, now) }
                itemsIndexed(items, key = { _, e -> e.id }) { i, e ->
                    EventRow(c, e, first = i == 0, last = i == items.lastIndex,
                        onClick = { editId = e.id }, modifier = Modifier.animateItem())
                }
            }
            if (days.size > daysShown) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { daysShown += 14 }) { Text(s["showMore"]) }
                    }
                }
            }
        }
    }

    editId?.let { id ->
        EditSheet(c, id, onDone = { editId = null }, onDeleted = { e ->
            showUndo("${s["deleted"]}: ${s[e.type]} · ${hhmm(e.t)}") { c.restore(e.id) }
        })
    }
    if (whoOpen) WhoSheet(c) { whoOpen = false }
}

@Composable
private fun TopBar(c: AppController, onWho: () -> Unit, onSettings: () -> Unit, onSummary: () -> Unit) {
    val s = LocalStrings.current
    val me = c.me()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KacperOps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                SyncDot(c.syncState)
            }
            Text(s["tagline"], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(onClick = onWho, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                val name = c.profileName(me, s)
                Avatar(me, name, 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        IconButton(onClick = onSummary) { Icon(Icons.Outlined.BarChart, contentDescription = s["summary"]) }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = s["settings"]) }
    }
}

/** The four big type buttons, docked at the bottom where the thumb is. */
@Composable
private fun TypePad(onPick: (String) -> Unit) {
    val s = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(s["whatNow"], style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeButton(Kind.PEE, 112, Modifier.weight(1f), onPick)
                TypeButton(Kind.POOP, 112, Modifier.weight(1f), onPick)
            }
            TypeButton(Kind.FEED, 96, Modifier.fillMaxWidth(), onPick)
        }
    }
}

@Composable
private fun TypeButton(type: String, height: Int, modifier: Modifier, onPick: (String) -> Unit) {
    val s = LocalStrings.current
    val cat = LocalCats.current.of(type)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "press")
    Surface(
        onClick = { onPick(type) },
        interactionSource = interaction,
        shape = RoundedCornerShape(28.dp),
        color = cat.container,
        border = BorderStroke(1.5.dp, cat.accent.copy(alpha = 0.3f)),
        modifier = modifier.height(height.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        if (height >= 100) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                KindIcon(type, cat.accent, 44.dp)
                Spacer(Modifier.height(6.dp))
                Text(s[type], fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                KindIcon(type, cat.accent, 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(s[type], fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Today's totals: ml for feeds, counts for pee / poop. No "time since". */
@Composable
private fun SinceRow(events: List<Event>, now: Long) {
    val s = LocalStrings.current
    val today = events.filter { it.t >= dayStart(now) }
    val feeds = today.filter { it.isFeed }
    val ml = feeds.sumOf { it.ml ?: 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TotalTile(Kind.FEED, s["feed"], "$ml", s["ml"], "${feeds.size}× · ${s["todayN"]}", Modifier.weight(1f))
        TotalTile(Kind.PEE, s["pee"], "${today.count { it.isPee }}", null, s["todayN"], Modifier.weight(1f))
        TotalTile(Kind.POOP, s["poop"], "${today.count { it.isPoop }}", null, s["todayN"], Modifier.weight(1f))
    }
}

@Composable
private fun TotalTile(type: String, label: String, value: String, unit: String?, sub: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindIcon(type, LocalCats.current.of(type).accent, 14.dp)
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, style = Tnum, maxLines = 1)
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun DayHeader(day: Long, items: List<Event>, now: Long) {
    val s = LocalStrings.current
    val feeds = items.count { it.isFeed }
    val ml = items.sumOf { if (it.isFeed) it.ml ?: 0 else 0 }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(day, now, s), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Total(Kind.FEED, "$feeds" + if (ml > 0) " · $ml ${s["ml"]}" else "")
        Total(Kind.PEE, "${items.count { it.isPee }}")
        Total(Kind.POOP, "${items.count { it.isPoop }}")
    }
}

@Composable
private fun Total(type: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
        KindIcon(type, LocalCats.current.of(type).accent, 14.dp)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun detailText(e: Event, s: Strings): String = buildList {
    sideLabel(e.side, s)?.let { add(it) }
    e.ml?.let { add("$it ${s["ml"]}") }
    e.note?.let { add(it) }
}.joinToString(" · ")

@Composable
private fun EventRow(c: AppController, e: Event, first: Boolean, last: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val s = LocalStrings.current
    val cat = LocalCats.current.of(e.type)
    val p = e.by?.let { c.db.profiles[it] }
    val big = 22.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) big else small, topEnd = if (first) big else small,
        bottomStart = if (last) big else small, bottomEnd = if (last) big else small,
    )
    Surface(
        onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(bottom = 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hhmm(e.t), fontWeight = FontWeight.Bold, fontSize = 17.sp, style = Tnum, modifier = Modifier.width(56.dp))
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(cat.container),
                contentAlignment = Alignment.Center,
            ) { KindIcon(e.type, cat.accent, if (e.type == Kind.BOTH) 17.dp else 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s[e.type], fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                val d = detailText(e, s)
                if (d.isNotEmpty()) {
                    Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Avatar(p, c.profileName(p, s), 26.dp)
        }
    }
}
