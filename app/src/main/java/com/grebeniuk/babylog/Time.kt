package com.grebeniuk.babylog

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val MIN = 60_000L
const val HOUR = 60 * MIN
const val DAY = 24 * HOUR

private fun cal(ts: Long) = Calendar.getInstance().apply { timeInMillis = ts }

fun floor10(ts: Long): Long = cal(ts).apply {
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    set(Calendar.MINUTE, get(Calendar.MINUTE) / 10 * 10)
}.timeInMillis

fun hourStart(ts: Long): Long = cal(ts).apply {
    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun dayStart(ts: Long): Long = cal(ts).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun hourOf(ts: Long) = cal(ts).get(Calendar.HOUR_OF_DAY)
fun minuteOf(ts: Long) = cal(ts).get(Calendar.MINUTE)

fun withHour(ts: Long, h: Int): Long = cal(ts).apply { set(Calendar.HOUR_OF_DAY, h) }.timeInMillis
fun withMinute(ts: Long, m: Int): Long = cal(ts).apply { set(Calendar.MINUTE, m) }.timeInMillis
fun plusDays(ts: Long, d: Int): Long = cal(ts).apply { add(Calendar.DAY_OF_MONTH, d) }.timeInMillis

fun hhmm(ts: Long): String = String.format(Locale.ROOT, "%02d:%02d", hourOf(ts), minuteOf(ts))
fun two(n: Int): String = String.format(Locale.ROOT, "%02d", n)

fun dayLabel(ts: Long, now: Long, s: Strings): String {
    val d = dayStart(ts)
    return when (d) {
        dayStart(now) -> s["today"]
        dayStart(plusDays(now, -1)) -> s["yesterday"]
        else -> SimpleDateFormat("EEE, d MMM", Locale.forLanguageTag(s.lang)).format(Date(ts))
            .replaceFirstChar { it.titlecase(Locale.forLanguageTag(s.lang)) }
    }
}

/** "2 h 05" style, used on the tiles. */
fun shortAgo(ms: Long, s: Strings): String {
    val m = (ms / MIN).coerceAtLeast(0)
    if (m < 60) return "$m ${s["min"]}"
    val h = m / 60
    if (h >= 48) return "${h / 24} ${s["d"]}"
    return "$h ${s["h"]} ${two((m % 60).toInt())}"
}

fun agoText(ms: Long, s: Strings): String {
    if (ms < 5 * MIN) return s["justNow"]
    val m = ms / MIN
    if (m < 60) return "$m ${s["min"]} ${s["ago"]}"
    val h = m / 60
    val mm = m % 60
    if (h < 48) return "$h ${s["h"]}" + (if (mm > 0) " $mm ${s["min"]}" else "") + " ${s["ago"]}"
    return "${h / 24} ${s["d"]} ${s["ago"]}"
}
