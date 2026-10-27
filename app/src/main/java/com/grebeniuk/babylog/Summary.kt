package com.grebeniuk.babylog

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Totals for one calendar day. */
data class DayStat(val day: Long, val feeds: Int, val ml: Int, val pee: Int, val poop: Int) {
    val avgMl: Int get() = if (feeds > 0) Math.round(ml.toDouble() / feeds).toInt() else 0
}

/**
 * Chronological per-day totals (oldest first), including empty days.
 * [days] = how many days back including today; null = since the first entry.
 */
fun dailyStats(events: List<Event>, days: Int?, now: Long): List<DayStat> {
    val today = dayStart(now)
    val first = events.minOfOrNull { dayStart(it.t) } ?: today
    // never start before the first entry (no empty days before the baby's log begins)
    var d = if (days == null) first else maxOf(first, dayStart(plusDays(today, -(days - 1))))
    val byDay = events.groupBy { dayStart(it.t) }
    val out = mutableListOf<DayStat>()
    while (d <= today) {
        val ev = byDay[d].orEmpty()
        val feeds = ev.filter { it.isFeed }
        out += DayStat(d, feeds.size, feeds.sumOf { it.ml ?: 0 }, ev.count { it.isPee }, ev.count { it.isPoop })
        d = dayStart(plusDays(d, 1))
    }
    return out
}

/** Averages over complete days only (today is still in progress). */
data class Averages(val days: Int, val ml: Double, val feeds: Double, val pee: Double, val poop: Double)

fun averages(stats: List<DayStat>, now: Long): Averages {
    val full = stats.filter { it.day < dayStart(now) }.dropWhile { it.feeds + it.pee + it.poop == 0 }
    if (full.isEmpty()) return Averages(0, 0.0, 0.0, 0.0, 0.0)
    val n = full.size.toDouble()
    return Averages(full.size, full.sumOf { it.ml } / n, full.sumOf { it.feeds } / n,
        full.sumOf { it.pee } / n, full.sumOf { it.poop } / n)
}

private fun fmt1(v: Double) = if (v >= 10) Math.round(v).toString() else String.format(Locale.ROOT, "%.1f", v)

private fun shortDate(ts: Long, lang: String) = SimpleDateFormat("d.MM", Locale.forLanguageTag(lang)).format(Date(ts))
private fun longDate(ts: Long, lang: String) =
    SimpleDateFormat("EEE d.MM.yyyy", Locale.forLanguageTag(lang)).format(Date(ts))

private val PERIODS = listOf<Int?>(7, 14, 30, null)

// ===================================================================== screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(c: AppController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val cats = LocalCats.current
    val ctx = LocalContext.current
    var periodIx by rememberSaveable { mutableIntStateOf(1) }
    val period = PERIODS[periodIx]
    val now = c.now
    val events = remember(c.db) { c.db.liveEvents() }
    val stats = remember(events, period, dayStart(now)) { dailyStats(events, period, now) }
    val avg = remember(stats) { averages(stats, now) }

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text(s["summary"], style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { shareSummaryPdf(ctx, c, stats, period, s) }) {
                Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(s["sharePdf"])
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PERIODS.forEachIndexed { i, p ->
                        SegmentedButton(selected = periodIx == i, onClick = { periodIx = i },
                            shape = SegmentedButtonDefaults.itemShape(i, PERIODS.size)) {
                            Text(if (p == null) s["all"] else "$p ${s["d"]}", maxLines = 1)
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s["avgPerDay"], style = MaterialTheme.typography.titleMedium)
                        Text(if (avg.days > 0) "${avg.days} ${s["fullDays"]}" else s["noFullDays"],
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AvgCell(Kind.FEED, fmt1(avg.ml), s["ml"], Modifier.weight(1f))
                            AvgCell(Kind.FEED, fmt1(avg.feeds), s["feedsN"], Modifier.weight(1f))
                            AvgCell(Kind.PEE, fmt1(avg.pee), s["pee"], Modifier.weight(1f))
                            AvgCell(Kind.POOP, fmt1(avg.poop), s["poop"], Modifier.weight(1f))
                        }
                    }
                }
            }
            item { ChartCard("${s["feed"]} · ${s["ml"]} / ${s["day"]}", stats, cats.feed.accent) { it.ml } }
            item { ChartCard("${s["pee"]} / ${s["day"]}", stats, cats.pee.accent) { it.pee } }
            item { ChartCard("${s["poop"]} / ${s["day"]}", stats, cats.poop.accent) { it.poop } }
            item { TableHeader() }
            items(stats.reversed(), key = { it.day }) { d -> TableRow(d, isToday = d.day == dayStart(now)) }
        }
    }
}

@Composable
private fun AvgCell(type: String, value: String, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        KindIcon(type, LocalCats.current.of(type).accent, 16.dp)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Tnum)
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ChartCard(title: String, stats: List<DayStat>, color: Color, value: (DayStat) -> Int) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelColor = cs.onSurfaceVariant
    val valueColor = cs.onSurface
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val n = stats.size.coerceAtLeast(1)
                val max = (stats.maxOfOrNull(value) ?: 0).coerceAtLeast(1)
                val labelH = 16.dp.toPx()
                val topPad = 16.dp.toPx()
                val chartH = size.height - labelH - topPad
                val slot = size.width / n
                val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                val labelEvery = when { n <= 10 -> 1; n <= 16 -> 2; n <= 31 -> 4; else -> n / 8 }
                val small = TextStyle(fontSize = 10.sp, color = labelColor)
                val bold = TextStyle(fontSize = 10.sp, color = valueColor, fontWeight = FontWeight.Bold)
                stats.forEachIndexed { i, d ->
                    val v = value(d)
                    val h = chartH * v / max
                    val x = slot * i + (slot - barW) / 2
                    val y = topPad + chartH - h
                    if (v > 0) {
                        drawRoundRect(color, Offset(x, y), Size(barW, h), CornerRadius(5.dp.toPx()))
                        if (n <= 16) {
                            val t = measurer.measure("$v", bold)
                            drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2, y - t.size.height))
                        }
                    }
                    val showLabel = (i % labelEvery == 0 && n - 1 - i >= labelEvery) || i == n - 1
                    if (showLabel) {
                        val t = measurer.measure(shortDate(d.day, s.lang), small)
                        drawText(t, topLeft = Offset((x + barW / 2 - t.size.width / 2).coerceIn(0f, size.width - t.size.width),
                            size.height - t.size.height))
                    }
                }
            }
        }
    }
}

private val COLS = listOf(1.6f, 0.9f, 1.1f, 1f, 0.9f, 0.9f)

@Composable
private fun TableHeader() {
    val s = LocalStrings.current
    val st = MaterialTheme.typography.labelMedium
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        listOf(s["date"], s["feedsN"], s["ml"], s["avgMl"], s["pee"], s["poop"]).forEachIndexed { i, h ->
            Text(h, style = st, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(COLS[i]))
        }
    }
}

@Composable
private fun TableRow(d: DayStat, isToday: Boolean) {
    val s = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(COLS[0])) {
                Text(shortDate(d.day, s.lang), fontWeight = FontWeight.SemiBold, style = Tnum)
                if (isToday) Text(s["soFar"], style = MaterialTheme.typography.labelSmall, color = cs.primary)
            }
            listOf("${d.feeds}", "${d.ml}", if (d.feeds > 0) "${d.avgMl}" else "–", "${d.pee}", "${d.poop}")
                .forEachIndexed { i, v ->
                    Text(v, style = Tnum, fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.End, modifier = Modifier.weight(COLS[i + 1]))
                }
        }
    }
}

// ===================================================================== PDF

private fun shareSummaryPdf(ctx: Context, c: AppController, stats: List<DayStat>, period: Int?, s: Strings) {
    val file = writeSummaryPdf(ctx, c, stats, period, s)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** A4 report: title, averages, ml/day chart, pee/poop chart, daily table (paginated). */
fun writeSummaryPdf(ctx: Context, c: AppController, stats: List<DayStat>, period: Int?, s: Strings): File {
    val now = System.currentTimeMillis()
    val avg = averages(stats, now)
    val W = 595; val H = 842; val M = 40f
    val doc = PdfDocument()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF222222.toInt() }
    val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val muted = Paint(text).apply { color = 0xFF777777.toInt() }
    val line = Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.7f }
    val feedC = Color(0xFF2F6FD6).toArgb(); val peeC = Color(0xFFD9A400).toArgb(); val poopC = Color(0xFF85552F).toArgb()

    var pageNo = 0
    var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
    var cv = page.canvas
    var y = M

    // header
    cv.drawText("KacperOps — ${s["summary"]}", M, y + 16f, Paint(bold).apply { textSize = 20f }); y += 30f
    val from = stats.firstOrNull()?.day ?: now
    cv.drawText("${longDate(from, s.lang)} – ${longDate(stats.lastOrNull()?.day ?: now, s.lang)}" +
        (if (period == null) "" else "  ($period ${s["d"]})"), M, y + 10f, muted); y += 14f
    cv.drawText("${s["generated"]}: ${longDate(now, s.lang)} ${hhmm(now)}", M, y + 10f, muted); y += 26f

    // averages
    cv.drawText("${s["avgPerDay"]} (${avg.days} ${s["fullDays"]})", M, y + 12f, Paint(bold).apply { textSize = 12f }); y += 22f
    val cellW = (W - 2 * M) / 4
    listOf(fmt1(avg.ml) to s["ml"], fmt1(avg.feeds) to s["feedsN"], fmt1(avg.pee) to s["pee"], fmt1(avg.poop) to s["poop"])
        .forEachIndexed { i, (v, l) ->
            cv.drawText(v, M + i * cellW, y + 18f, Paint(bold).apply { textSize = 18f })
            cv.drawText(l, M + i * cellW, y + 32f, muted)
        }
    y += 50f

    // charts
    fun chart(title: String, h: Float, series: List<Pair<Int, (DayStat) -> Int>>) {
        cv.drawText(title, M, y + 10f, bold); y += 16f
        val n = stats.size.coerceAtLeast(1)
        val max = series.maxOf { (_, f) -> stats.maxOfOrNull(f) ?: 0 }.coerceAtLeast(1)
        val slot = (W - 2 * M) / n
        val groupW = (slot * 0.7f).coerceAtMost(30f)
        val barW = groupW / series.size
        val base = y + h
        cv.drawLine(M, base, W - M, base, line)
        val every = when { n <= 14 -> 1; n <= 31 -> 3; else -> n / 10 }
        stats.forEachIndexed { i, d ->
            series.forEachIndexed { si, (col, f) ->
                val v = f(d)
                if (v > 0) {
                    val bh = (h - 12f) * v / max
                    val x = M + slot * i + (slot - groupW) / 2 + si * barW
                    cv.drawRect(x, base - bh, x + barW - 1f, base, Paint().apply { color = col })
                    if (n <= 16) cv.drawText("$v", x, base - bh - 2f, Paint(muted).apply { textSize = 7f })
                }
            }
            if (i % every == 0) cv.drawText(shortDate(d.day, s.lang), M + slot * i + 1f, base + 10f, Paint(muted).apply { textSize = 7f })
        }
        y = base + 22f
    }
    chart("${s["feed"]} · ${s["ml"]} / ${s["day"]}", 110f, listOf(feedC to { d: DayStat -> d.ml }))
    chart("${s["pee"]} / ${s["poop"]} · ${s["day"]}", 70f, listOf(peeC to { d: DayStat -> d.pee }, poopC to { d: DayStat -> d.poop }))

    // table
    val heads = listOf(s["date"], s["feedsN"], s["ml"], s["avgMl"], s["pee"], s["poop"])
    val xs = listOf(M, M + 150f, M + 230f, M + 310f, M + 390f, M + 460f)
    fun header() {
        heads.forEachIndexed { i, h -> cv.drawText(h, xs[i], y + 10f, bold) }
        y += 14f; cv.drawLine(M, y, W - M, y, line); y += 4f
    }
    header()
    stats.reversed().forEach { d ->
        if (y > H - M - 16f) {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create()); cv = page.canvas; y = M
            header()
        }
        val isToday = d.day == dayStart(now)
        val cells = listOf(longDate(d.day, s.lang) + if (isToday) " (${s["soFar"]})" else "",
            "${d.feeds}", "${d.ml}", if (d.feeds > 0) "${d.avgMl}" else "–", "${d.pee}", "${d.poop}")
        cells.forEachIndexed { i, v -> cv.drawText(v, xs[i], y + 10f, if (i == 2) bold else text) }
        y += 14f; cv.drawLine(M, y, W - M, y, line); y += 2f
    }
    doc.finishPage(page)

    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val name = "KacperOps-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now)) + ".pdf"
    val f = File(dir, name)
    FileOutputStream(f).use { doc.writeTo(it) }
    doc.close()
    return f
}
