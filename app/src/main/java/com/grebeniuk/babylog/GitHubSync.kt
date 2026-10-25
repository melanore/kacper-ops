package com.grebeniuk.babylog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/**
 * Syncs through one JSON file in a (private) GitHub repo using the REST "contents" API.
 *
 *   GET  /repos/{repo}/contents/log.json?ref={branch}   (with If-None-Match, so polling is free)
 *   PUT  /repos/{repo}/contents/log.json                (with the blob sha → optimistic locking)
 *
 * Two phones writing at once: the slower PUT gets 409, re-reads, merges and retries.
 */
class GitHubSync {
    companion object {
        const val PATH = "log.json"
        private const val API = "https://api.github.com"
    }

    /** Error key from [Strings] plus optional detail. */
    data class Failure(val key: String, val detail: String = "")

    private var configKey = ""
    private var etag: String? = null
    private var sha: String? = null
    var remote: Db? = null
        private set

    fun reset() {
        configKey = ""; etag = null; sha = null; remote = null
    }

    /**
     * @param applyRemote merges the remote snapshot into local state and returns the merged local state.
     * @return null on success.
     */
    suspend fun run(cfg: Config, author: String, applyRemote: (Db) -> Db): Failure? {
        val key = "${cfg.repo}@${cfg.branch}#${cfg.token.hashCode()}"
        if (key != configKey) { reset(); configKey = key }

        repeat(4) {
            fetch(cfg)?.let { return it }
            val r = remote ?: Db()
            val merged = applyRemote(r)
            if (!merged.aheadOf(r)) return null
            when (val p = put(cfg, merged, commitMessage(merged, r, author))) {
                is PutResult.Ok -> {
                    remote = merged; sha = p.sha; etag = null
                    return null
                }
                PutResult.Conflict -> { etag = null } // someone else wrote; re-read and merge
                is PutResult.Err -> return p.failure
            }
        }
        return Failure("errConflict")
    }

    // ---------------------------------------------------------------- GET

    private suspend fun fetch(cfg: Config): Failure? {
        val base = "$API/repos/${cfg.repo}"
        val r = http("GET", "$base/contents/$PATH?ref=${enc(cfg.branch)}", cfg.token, etag = etag)
        return when (r.code) {
            304 -> null
            200 -> try {
                val o = JSONObject(r.body)
                val content = o.optString("content", "")
                val text = if (content.isBlank() && o.optLong("size") > 0) {
                    // files over 1 MB come without inline content
                    val raw = http("GET", "$base/contents/$PATH?ref=${enc(cfg.branch)}", cfg.token,
                        accept = "application/vnd.github.raw+json")
                    if (raw.code != 200) return failureFor(raw)
                    raw.body
                } else String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
                remote = if (text.isBlank()) Db() else Db.fromJson(JSONObject(text))
                sha = o.getString("sha")
                etag = r.etag
                null
            } catch (e: Exception) {
                Failure("errBad", e.message ?: "")
            }
            404 -> {
                val f = ensureBranch(cfg)
                if (f == null) { remote = Db(); sha = null; etag = null }
                f
            }
            else -> failureFor(r)
        }
    }

    /** File missing: either the branch is missing (create it) or the file just doesn't exist yet. */
    private suspend fun ensureBranch(cfg: Config): Failure? {
        val base = "$API/repos/${cfg.repo}"
        val b = http("GET", "$base/branches/${enc(cfg.branch)}", cfg.token)
        if (b.code == 200) return null
        if (b.code != 404) return failureFor(b)
        val repo = http("GET", base, cfg.token)
        if (repo.code != 200) return failureFor(repo)
        val def = JSONObject(repo.body).optString("default_branch", "main")
        val ref = http("GET", "$base/git/ref/heads/${enc(def)}", cfg.token)
        if (ref.code == 404 || ref.code == 409) return Failure("errEmpty")
        if (ref.code != 200) return failureFor(ref)
        val head = JSONObject(ref.body).getJSONObject("object").getString("sha")
        val body = JSONObject().put("ref", "refs/heads/${cfg.branch}").put("sha", head).toString()
        val c = http("POST", "$base/git/refs", cfg.token, body = body)
        return if (c.code == 201 || c.code == 422) null else failureFor(c)
    }

    // ---------------------------------------------------------------- PUT

    private sealed interface PutResult {
        data class Ok(val sha: String) : PutResult
        data object Conflict : PutResult
        data class Err(val failure: Failure) : PutResult
    }

    private suspend fun put(cfg: Config, db: Db, message: String): PutResult {
        val json = db.toJson().toString(1)
        val body = JSONObject().apply {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8)))
            put("branch", cfg.branch)
            sha?.let { put("sha", it) }
        }.toString()
        val r = http("PUT", "$API/repos/${cfg.repo}/contents/$PATH", cfg.token, body = body)
        return when (r.code) {
            200, 201 -> try {
                PutResult.Ok(JSONObject(r.body).getJSONObject("content").getString("sha"))
            } catch (e: Exception) {
                PutResult.Err(Failure("errBad", e.message ?: ""))
            }
            409, 422 -> PutResult.Conflict
            else -> PutResult.Err(failureFor(r))
        }
    }

    private fun commitMessage(merged: Db, remote: Db, author: String): String {
        val changed = merged.events.values.filter { e ->
            val r = remote.events[e.id]
            (r == null && e.updated > 0) || (r != null && e.updated > r.updated)
        }
        val who = author.ifBlank { "phone" }
        if (changed.size == 1) {
            val e = changed[0]
            val verb = when {
                e.deleted -> "removed"
                remote.events[e.id] != null -> "edited"
                else -> "added"
            }
            return "$verb ${e.type} ${hhmm(e.t)} · $who"
        }
        return if (changed.isEmpty()) "people updated · $who" else "sync ${changed.size} entries · $who"
    }

    // ---------------------------------------------------------------- HTTP

    private data class Resp(val code: Int, val body: String, val etag: String?)

    private fun failureFor(r: Resp): Failure = when (r.code) {
        0 -> Failure("errOffline")
        401 -> Failure("errToken")
        403, 429 -> Failure("errForbidden")
        404 -> Failure("errNotFound")
        else -> Failure("errBad", "HTTP ${r.code}")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun http(
        method: String,
        url: String,
        token: String,
        body: String? = null,
        accept: String = "application/vnd.github+json",
        etag: String? = null,
    ): Resp = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        try {
            c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                useCaches = false
                setRequestProperty("Authorization", "Bearer ${token.trim()}")
                setRequestProperty("Accept", accept)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "KacperOps-Android")
                if (etag != null) setRequestProperty("If-None-Match", etag)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = c.responseCode
            val stream = when {
                code == 304 -> null
                code >= 400 -> c.errorStream
                else -> c.inputStream
            }
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            Resp(code, text, c.getHeaderField("ETag"))
        } catch (e: Exception) {
            Resp(0, e.message ?: "", null)
        } finally {
            c?.disconnect()
        }
    }
}
