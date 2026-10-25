package com.grebeniuk.babylog

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.random.Random

enum class SyncState { OFF, SYNCING, OK, PENDING, ERROR }

/** All app state + actions. Plain class (no Android Context) so screenshot tests can build one. */
class AppController(private val store: Store?, private val scope: CoroutineScope) {
    var db by mutableStateOf(Db().withSeeds())
        private set
    var cfg by mutableStateOf(Config())
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())

    /** Custom time picked in the time card; null = "now". Reverts to now after [CUSTOM_TTL] idle. */
    var sel by mutableStateOf<Long?>(null)
        private set
    private var selTouched = 0L

    var syncing by mutableStateOf(false)
        private set
    var syncError by mutableStateOf<GitHubSync.Failure?>(null)
        private set
    private var remoteSnap by mutableStateOf<Db?>(null)

    private val gh = GitHubSync()
    private var syncRunning = false
    private var syncAgain = false
    private var debounceJob: Job? = null
    private var pollJob: Job? = null
    private val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    companion object {
        const val CUSTOM_TTL = 3 * MIN
    }

    init {
        store?.let { s ->
            db = Db.fromJson(s.read("db")).withSeeds()
            cfg = Config.fromJson(s.read("cfg"))
        }
    }

    // ------------------------------------------------------------ time selection

    private fun customTime(now: Long): Long? =
        sel?.takeIf { now - selTouched < CUSTOM_TTL && it < floor10(now) }

    fun effectiveTime(now: Long = this.now): Long = customTime(now) ?: floor10(now)
    fun isNowMode(now: Long = this.now): Boolean = customTime(now) == null

    fun setTime(t: Long) {
        val n = System.currentTimeMillis()
        sel = if (t >= floor10(n)) null else t
        selTouched = n
    }

    fun pickHour(hourTs: Long) {
        val m = minuteOf(effectiveTime())
        setTime(minOf(hourTs + m * MIN, floor10(System.currentTimeMillis())))
    }

    fun resetTime() { sel = null }

    // ------------------------------------------------------------ events

    private fun uid() = System.currentTimeMillis().toString(36) + Random.nextLong(1L shl 40).toString(36)

    fun log(type: String): Event {
        val n = System.currentTimeMillis()
        val e = Event(id = uid(), type = type, t = effectiveTime(n), by = cfg.me, created = n, updated = n)
        if (customTime(n) != null) selTouched = n
        putEvent(e)
        return e
    }

    fun update(e: Event) = putEvent(e)
    fun delete(id: String) { db.events[id]?.let { putEvent(it.copy(deleted = true)) } }
    fun restore(id: String) { db.events[id]?.let { putEvent(it.copy(deleted = false)) } }

    private fun putEvent(e: Event) {
        val stamped = e.copy(updated = maxOf(System.currentTimeMillis(), e.updated + 1))
        db = db.copy(events = db.events + (stamped.id to stamped))
        persistDb(); scheduleSync()
    }

    // ------------------------------------------------------------ people

    fun profileName(p: Profile?, s: Strings): String = when {
        p == null -> "?"
        p.name.isNotBlank() -> p.name
        else -> s[p.def]
    }

    fun me(): Profile? = cfg.me?.let { db.profiles[it] }?.takeIf { !it.deleted }

    fun setMe(id: String) { cfg = cfg.copy(me = id); persistCfg() }

    fun renameProfile(id: String, name: String) {
        db.profiles[id]?.let { if (it.name != name) putProfile(it.copy(name = name)) }
    }

    fun addProfile(): Profile {
        val order = (db.profiles.values.maxOfOrNull { it.order } ?: 0) + 1
        val p = Profile(id = "p-" + uid(), def = "other", order = order,
            color = Profile.PALETTE[(order - 1).mod(Profile.PALETTE.size)])
        putProfile(p)
        return p
    }

    fun deleteProfile(id: String) {
        db.profiles[id]?.let { putProfile(it.copy(deleted = true)) }
        if (cfg.me == id) db.liveProfiles().firstOrNull()?.let { setMe(it.id) }
    }

    private fun putProfile(p: Profile) {
        val stamped = p.copy(updated = maxOf(System.currentTimeMillis(), p.updated + 1))
        db = db.copy(profiles = db.profiles + (stamped.id to stamped))
        persistDb(); scheduleSync()
    }

    // ------------------------------------------------------------ settings

    fun setLang(l: String) { cfg = cfg.copy(lang = l); persistCfg() }
    fun setTheme(t: String) { cfg = cfg.copy(theme = t); persistCfg() }

    fun setSync(repo: String, branch: String, token: String) {
        val r = repo.trim()
            .removePrefix("https://").removePrefix("http://").removePrefix("github.com/")
            .removeSuffix(".git").trim('/')
        cfg = cfg.copy(repo = r, branch = branch.trim().ifEmpty { "data" }, token = token.trim())
        persistCfg()
        gh.reset(); remoteSnap = null; syncError = null
        syncNow()
    }

    fun setupCode(): String = Uri.Builder().scheme("kacperops").authority("sync")
        .appendQueryParameter("repo", cfg.repo)
        .appendQueryParameter("branch", cfg.branch)
        .appendQueryParameter("token", cfg.token)
        .build().toString()

    /** Accepts a pairing link anywhere inside [text]. Returns true if it was applied. */
    fun applySetupCode(text: String): Boolean {
        val link = Regex("""kacperops://\S+""").find(text)?.value ?: return false
        val u = runCatching { Uri.parse(link) }.getOrNull() ?: return false
        val repo = u.getQueryParameter("repo").orEmpty()
        val token = u.getQueryParameter("token").orEmpty()
        if (!repo.contains('/') || token.isBlank()) return false
        setSync(repo, u.getQueryParameter("branch") ?: "data", token)
        return true
    }

    fun csv(s: Strings): String {
        val rows = mutableListOf(listOf("date", "time", "type", "side", "ml", "note", "by"))
        db.liveEvents().reversed().forEach { e ->
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(e.t))
            rows += listOf(d, hhmm(e.t), e.type, e.side.orEmpty(), e.ml?.toString().orEmpty(), e.note.orEmpty(),
                profileName(e.by?.let { db.profiles[it] }, s))
        }
        return rows.joinToString("\n") { r ->
            r.joinToString(",") { c -> if (c.any { it == ',' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c }
        }
    }

    // ------------------------------------------------------------ persistence

    private fun persistDb() {
        val s = store ?: return
        val text = db.toJson().toString()
        scope.launch(io) { s.write("db", text) }
    }

    private fun persistCfg() {
        val s = store ?: return
        val text = cfg.toJson().toString()
        scope.launch(io) { s.write("cfg", text) }
    }

    // ------------------------------------------------------------ sync

    val syncState: SyncState
        get() = when {
            !cfg.syncOn -> SyncState.OFF
            syncing -> SyncState.SYNCING
            syncError != null -> SyncState.ERROR
            remoteSnap == null || db.aheadOf(remoteSnap!!) -> SyncState.PENDING
            else -> SyncState.OK
        }

    private fun scheduleSync(delayMs: Long = 700) {
        if (!cfg.syncOn) return
        debounceJob?.cancel()
        debounceJob = scope.launch { delay(delayMs); runSync() }
    }

    fun syncNow() { scope.launch { runSync() } }

    private suspend fun runSync() {
        if (!cfg.syncOn) return
        if (syncRunning) { syncAgain = true; return }
        syncRunning = true; syncing = true
        val author = profileName(me(), Strings("en"))
        val failure = gh.run(cfg, author) { remote ->
            val merged = merge(db, remote)
            if (merged != db) { db = merged; persistDb() }
            db
        }
        remoteSnap = gh.remote
        syncError = failure
        if (failure == null) { cfg = cfg.copy(lastSync = System.currentTimeMillis()); persistCfg() }
        syncing = false; syncRunning = false
        if (syncAgain) { syncAgain = false; runSync() }
    }

    fun onResume() {
        now = System.currentTimeMillis()
        resetTime()
        syncNow()
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) { delay(30_000); runSync() }
        }
    }

    fun onPause() { pollJob?.cancel(); pollJob = null }
}
