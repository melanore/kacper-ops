package com.grebeniuk.babylog

import org.json.JSONObject
import java.util.Locale

object Kind {
    const val PEE = "pee"
    const val POOP = "poop"
    const val BOTH = "both"
    const val FEED = "feed"
    val all = listOf(PEE, POOP, BOTH, FEED)
}

/** One logged thing. Field names match the JSON written by v1 of the app. */
data class Event(
    val id: String,
    val type: String,
    val t: Long,
    val by: String?,
    val created: Long,
    val updated: Long,
    val deleted: Boolean = false,
    val side: String? = null, // L, R, LR, bottle
    val ml: Int? = null,
    val note: String? = null,
) {
    val isPee get() = type == Kind.PEE || type == Kind.BOTH
    val isPoop get() = type == Kind.POOP || type == Kind.BOTH
    val isFeed get() = type == Kind.FEED

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type); put("t", t)
        put("by", by ?: JSONObject.NULL)
        put("created", created); put("updated", updated)
        if (deleted) put("deleted", true)
        side?.let { put("side", it) }
        ml?.let { put("ml", it) }
        note?.let { put("note", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Event? = runCatching {
            Event(
                id = o.getString("id"),
                type = o.optString("type", Kind.PEE).takeIf { it in Kind.all } ?: Kind.PEE,
                t = o.getLong("t"),
                by = o.optStr("by"),
                created = o.optLong("created", o.optLong("t")),
                updated = o.optLong("updated", 0L),
                deleted = o.optBoolean("deleted", false),
                side = o.optStr("side"),
                ml = if (o.has("ml") && !o.isNull("ml")) o.optInt("ml").takeIf { it > 0 } else null,
                note = o.optStr("note"),
            )
        }.getOrNull()
    }
}

data class Profile(
    val id: String,
    val def: String = "other",
    val name: String = "",
    val order: Int = 0,
    val color: String = PALETTE[0],
    val updated: Long = 0,
    val deleted: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("def", def); put("name", name); put("order", order)
        put("color", color); put("updated", updated)
        if (deleted) put("deleted", true)
    }

    companion object {
        val PALETTE = listOf("#4E7FD9", "#D9578A", "#2E9E5B", "#E39B1B", "#8A63D2", "#0FA3B1", "#C2410C")

        fun fromJson(o: JSONObject): Profile? = runCatching {
            Profile(
                id = o.getString("id"),
                def = o.optString("def", "other"),
                name = o.optString("name", ""),
                order = o.optInt("order", 0),
                color = o.optString("color", PALETTE[0]),
                updated = o.optLong("updated", 0L),
                deleted = o.optBoolean("deleted", false),
            )
        }.getOrNull()

        val seeds = listOf(
            Profile("p-dad", "dad", "Max", 1, PALETTE[0]),
            Profile("p-mom", "mom", "Paulina", 2, PALETTE[1]),
            Profile("p-other", "other", "", 3, PALETTE[2]),
        )
    }
}

data class Db(
    val events: Map<String, Event> = emptyMap(),
    val profiles: Map<String, Profile> = emptyMap(),
) {
    /** Adds the default people if missing, and names untouched defaults Max / Paulina (no "Dad" / "Mom"). */
    fun withSeeds(): Db {
        val seeded = profiles + Profile.seeds.filter { it.id !in profiles }.associateBy { it.id }
        val named = seeded.mapValues { (_, p) ->
            val default = Profile.seeds.firstOrNull { it.id == p.id }?.name.orEmpty()
            if (p.name.isBlank() && p.updated == 0L && default.isNotEmpty()) p.copy(name = default) else p
        }
        return if (named == profiles) this else copy(profiles = named)
    }

    fun liveProfiles(): List<Profile> = profiles.values.filter { !it.deleted }.sortedBy { it.order }

    fun liveEvents(): List<Event> = events.values.filter { !it.deleted }
        .sortedWith(compareByDescending<Event> { it.t }.thenByDescending { it.created })

    /** Events sorted by time so the synced file gives readable git diffs. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("app", "KacperOps")
        put("profiles", JSONObject().apply {
            profiles.values.sortedBy { it.order }.forEach { put(it.id, it.toJson()) }
        })
        put("events", JSONObject().apply {
            events.values.sortedWith(compareBy<Event> { it.t }.thenBy { it.id }).forEach { put(it.id, it.toJson()) }
        })
    }

    companion object {
        fun fromJson(o: JSONObject?): Db {
            if (o == null) return Db()
            val ev = mutableMapOf<String, Event>()
            o.optJSONObject("events")?.let { e ->
                e.keys().forEach { k -> e.optJSONObject(k)?.let(Event::fromJson)?.let { ev[it.id] = it } }
            }
            val pr = mutableMapOf<String, Profile>()
            o.optJSONObject("profiles")?.let { p ->
                p.keys().forEach { k -> p.optJSONObject(k)?.let(Profile::fromJson)?.let { pr[it.id] = it } }
            }
            return Db(ev, pr)
        }
    }
}

/** Last-writer-wins merge per item; deletes are tombstones so they merge like edits. */
fun merge(local: Db, remote: Db): Db {
    fun <T> m(l: Map<String, T>, r: Map<String, T>, upd: (T) -> Long): Map<String, T> {
        var out: MutableMap<String, T>? = null
        for ((id, rv) in r) {
            val lv = l[id]
            if (lv == null || upd(rv) > upd(lv)) {
                if (out == null) out = l.toMutableMap()
                out[id] = rv
            }
        }
        return out ?: l
    }
    return Db(m(local.events, remote.events) { it.updated }, m(local.profiles, remote.profiles) { it.updated })
}

/** True when [this] holds something newer than [remote] (i.e. we need to push). */
fun Db.aheadOf(remote: Db): Boolean {
    fun <T> a(l: Map<String, T>, r: Map<String, T>, upd: (T) -> Long) = l.any { (id, lv) ->
        val rv = r[id]
        (rv == null && upd(lv) > 0) || (rv != null && upd(lv) > upd(rv))
    }
    return a(events, remote.events) { it.updated } || a(profiles, remote.profiles) { it.updated }
}

data class Config(
    val me: String? = null,
    val lang: String = guessLang(),
    val theme: String = "auto", // auto | light | dark
    val repo: String = "",
    val branch: String = "data",
    val token: String = "",
    val lastSync: Long = 0,
) {
    val syncOn get() = repo.contains('/') && token.isNotBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("me", me ?: JSONObject.NULL); put("lang", lang); put("theme", theme)
        put("repo", repo); put("branch", branch); put("token", token); put("lastSync", lastSync)
    }

    companion object {
        fun guessLang(): String = when (Locale.getDefault().language) {
            "pl" -> "pl"
            "uk", "ru" -> "uk"
            else -> "en"
        }

        fun fromJson(o: JSONObject?): Config {
            if (o == null) return Config()
            return Config(
                me = o.optStr("me"),
                lang = o.optString("lang", guessLang()).takeIf { it in listOf("en", "pl", "uk") } ?: "en",
                theme = o.optString("theme", "auto").takeIf { it in listOf("auto", "light", "dark") } ?: "auto",
                repo = o.optString("repo", ""),
                branch = o.optString("branch", "data").ifBlank { "data" },
                token = o.optString("token", ""),
                lastSync = o.optLong("lastSync", 0L),
            )
        }
    }
}

fun JSONObject.optStr(k: String): String? =
    if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotEmpty() } else null
