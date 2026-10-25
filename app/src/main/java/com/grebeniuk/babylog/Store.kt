package com.grebeniuk.babylog

import org.json.JSONObject
import java.io.File

/** Tiny JSON file store in the app's private folder. Same files (db.json, cfg.json) as v1. */
class Store(private val dir: File) {
    private fun file(key: String) = File(dir, "$key.json")

    fun read(key: String): JSONObject? = try {
        val f = file(key)
        if (f.exists()) JSONObject(f.readText(Charsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun write(key: String, text: String) {
        val f = file(key)
        val tmp = File(dir, "$key.json.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.delete(); tmp.renameTo(f)
        }
    }
}
