package com.honestreward.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiError(message: String, val code: Int = 0) : Exception(message)

/** Keeps the login token on the phone so the user stays signed in after closing the app. */
object Session {
    private const val KEY = "token"
    fun load(ctx: Context): Boolean {
        Api.token = ctx.getSharedPreferences("hr", Context.MODE_PRIVATE).getString(KEY, null)
        return Api.token != null
    }
    fun save(ctx: Context, t: String?) {
        Api.token = t
        val e = ctx.getSharedPreferences("hr", Context.MODE_PRIVATE).edit()
        if (t == null) e.remove(KEY) else e.putString(KEY, t)
        e.apply()
    }
}

object Api {
    var token: String? = null

    private fun once(method: String, path: String, body: JSONObject?): Any? {
        val c = URL(BuildConfig.API_BASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 20000; c.readTimeout = 30000
            c.setRequestProperty("Content-Type", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code >= 400) throw ApiError(runCatching { JSONObject(text).getString("error") }.getOrDefault("HTTP $code"), code)
            return when {
                text.isBlank() -> null
                text.trimStart().startsWith("[") -> JSONArray(text)
                else -> JSONObject(text)
            }
        } finally { c.disconnect() }
    }

    // GET requests are retried (slow/cold server); POSTs are never retried to avoid double actions.
    suspend fun call(method: String, path: String, body: JSONObject? = null): Any? = withContext(Dispatchers.IO) {
        var last: IOException? = null
        val tries = if (method == "GET") 3 else 1
        for (i in 1..tries) {
            try { return@withContext once(method, path, body) }
            catch (e: IOException) { last = e; if (i < tries) delay(1500) }
        }
        throw last ?: IOException("network error")
    }

    suspend fun obj(m: String, p: String, b: JSONObject? = null): JSONObject = call(m, p, b) as? JSONObject ?: throw ApiError("empty response")
    suspend fun arr(m: String, p: String): JSONArray = call(m, p) as? JSONArray ?: throw ApiError("empty response")
}
