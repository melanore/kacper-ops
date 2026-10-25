package com.grebeniuk.babylog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(c: AppController, onSettings: () -> Unit) {
    val s = LocalStrings.current
    val view = LocalView.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var feedFor by remember { mutableStateOf<String?>(null) }
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

    fun log(type: String) {
        view.tick()
        val e = c.log(type)
        showUndo("${Kind.emoji(type)}  ${s[type]} · ${hhmm(e.t)}") { c.delete(e.id) }
        if (type == Kind.FEED) feedFor = e.id
    }

    Scaffold(
        topBar = { TopBar(c, onWho = { whoOpen = true }, onSettings = onSettings) },
        snackbarHost = {
            SnackbarHost(snack) { Snackbar(it, shape = RoundedCornerShape(16.dp)) }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "time") { TimeCard(c, now) }
            item(key = "actions") { ActionGrid(Modifier.padding(top = 12.dp)) { log(it) } }
            item(key = "since") { SinceRow(events, now, Modifier.padding(top = 12.dp)) }
            item(key = "histTitle") { SectionTitle(s["history"], Modifier.padding(top = 6.dp)) }
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
                    EventRow(
                        c, e, first = i == 0, last = i == items.lastIndex,
                        onClick = { editId = e.id },
                        modifier = Modifier.animateItem(),
                    )
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

    feedFor?.let { id -> FeedSheet(c, id) { feedFor = null } }
    editId?.let { id ->
        EditSheet(c, id, onDone = { editId = null }, onDeleted = { e ->
            showUndo("${s["deleted"]} · ${s[e.type]} ${hhmm(e.t)}") { c.restore(e.id) }
        })
    }
    if (whoOpen) WhoSheet(c) { whoOpen = false }
}

@Composable
private fun TopBar(c: AppController, onWho: () -> Unit, onSettings: () -> Unit) {
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
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = s["settings"]) }
    }
}

@Composable
private fun TimeCard(c: AppController, now: Long) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val ts = c.effectiveTime(now)
    val isNow = c.isNowMode(now)
    val hourList = rememberLazyListState()
    LaunchedEffect(isNow) { if (isNow) hourList.animateScrollToItem(0) }

    Surface(shape = RoundedCornerShape(30.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = hhmm(ts),
                    transitionSpec = {
                        (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                    },
                    label = "bigTime",
                ) { txt ->
                    Text(txt, fontSize = 54.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, style = Tnum)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isNow) s["now"] else dayLabel(ts, now, s),
                        style = MaterialTheme.typography.titleMedium, color = if (isNow) cs.primary else cs.onSurface)
                    Text(if (isNow) dayLabel(ts, now, s) else agoText(now - ts, s),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Pill(s["now"], selected = isNow, onClick = { c.resetTime() }, fontSize = 15,
                    modifier = Modifier.height(40.dp).width(76.dp))
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(state = hourList, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(24) { i ->
                    val h = hourStart(now) - i * HOUR
                    Pill(
                        two(hourOf(h)),
                        selected = h == hourStart(ts),
                        onClick = { c.pickHour(h) },
                        sub = if (dayStart(h) != dayStart(now)) s["yest"] else null,
                        modifier = Modifier.size(width = 54.dp, height = 54.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in 0..50 step 10) {
                    val t = hourStart(ts) + m * MIN
                    Pill(
                        ":" + two(m),
                        selected = t == ts,
                        enabled = t <= now,
                        onClick = { c.setTime(t) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionGrid(modifier: Modifier, onLog: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Kind.PEE, true, Modifier.weight(1f), onLog)
            ActionTile(Kind.POOP, true, Modifier.weight(1f), onLog)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile(Kind.BOTH, false, Modifier.weight(1f), onLog)
            ActionTile(Kind.FEED, false, Modifier.weight(1f), onLog)
        }
    }
}

@Composable
private fun ActionTile(type: String, big: Boolean, modifier: Modifier, onLog: (String) -> Unit) {
    val s = LocalStrings.current
    val cat = LocalCats.current.of(type)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "press")
    Surface(
        onClick = { onLog(type) },
        interactionSource = interaction,
        shape = RoundedCornerShape(30.dp),
        color = cat.container,
        border = BorderStroke(1.5.dp, cat.accent.copy(alpha = 0.28f)),
        modifier = modifier.height(if (big) 132.dp else 100.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(Kind.emoji(type), fontSize = if (big) 46.sp else 30.sp)
            Spacer(Modifier.height(4.dp))
            Text(s[type], fontSize = if (big) 19.sp else 17.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SinceRow(events: List<Event>, now: Long, modifier: Modifier) {
    val s = LocalStrings.current
    val t0 = dayStart(now)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SinceTile("🍼", s["feed"], events, now, t0, 3, Modifier.weight(1f)) { it.isFeed }
        SinceTile("💧", s["pee"], events, now, t0, 0, Modifier.weight(1f)) { it.isPee }
        SinceTile("💩", s["poop"], events, now, t0, 0, Modifier.weight(1f)) { it.isPoop }
    }
}

@Composable
private fun SinceTile(
    emoji: String, label: String, events: List<Event>, now: Long, t0: Long, warnHours: Int,
    modifier: Modifier, match: (Event) -> Boolean,
) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val last = events.firstOrNull(match)
    val count = events.count { it.t >= t0 && match(it) }
    val late = last != null && warnHours > 0 && now - last.t > warnHours * HOUR
    Surface(shape = RoundedCornerShape(20.dp), color = if (late) cs.errorContainer else cs.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("$emoji $label", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                if (last != null) shortAgo(now - last.t, s) else s["never"],
                fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tnum, maxLines = 1,
                color = if (late) cs.onErrorContainer else cs.onSurface,
            )
            Text(
                (if (last != null) hhmm(last.t) + " · " else "") + "$count ${s["todayN"]}",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayHeader(day: Long, items: List<Event>, now: Long) {
    val s = LocalStrings.current
    val feeds = items.count { it.isFeed }
    val ml = items.sumOf { if (it.isFeed) it.ml ?: 0 else 0 }
    val pee = items.count { it.isPee }
    val poop = items.count { it.isPoop }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(dayLabel(day, now, s), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            "🍼 $feeds" + (if (ml > 0) " ($ml ${s["ml"]})" else "") + "  ·  💧 $pee  ·  💩 $poop",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hhmm(e.t), fontWeight = FontWeight.Bold, fontSize = 16.sp, style = Tnum, modifier = Modifier.width(54.dp))
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(cat.container),
                contentAlignment = Alignment.Center,
            ) { Text(Kind.emoji(e.type), fontSize = if (e.type == Kind.BOTH) 13.sp else 20.sp, maxLines = 1) }
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
