package citu.edu.stathis.mobile.features.tasks.presentation

import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Debug-session NDJSON logger (session b7147e). Posts to the Cursor debug ingest server.
 * Safe no-op on JVM unit tests / offline devices.
 */
object DebugSessionLog {
    private const val ENDPOINT =
        "http://127.0.0.1:7316/ingest/495f4aba-74a7-432b-b062-a71e4ed7ed12"
    private const val EMULATOR_ENDPOINT =
        "http://10.0.2.2:7316/ingest/495f4aba-74a7-432b-b062-a71e4ed7ed12"
    private const val SESSION = "b7147e"
    private val executor = Executors.newSingleThreadExecutor()

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "verify1"
    ) {
        try {
            val dataJson = data.entries.joinToString(",") { (k, v) ->
                val value = when (v) {
                    null -> "null"
                    is Number, is Boolean -> v.toString()
                    else -> "\"${v.toString().replace("\"", "'").replace("\n", " ")}\""
                }
                "\"$k\":$value"
            }
            val payload =
                """{"sessionId":"$SESSION","hypothesisId":"$hypothesisId","location":"$location","message":"$message","timestamp":${System.currentTimeMillis()},"runId":"$runId","data":{$dataJson}}"""
            try {
                android.util.Log.i("DbgSession_$SESSION", "$hypothesisId|$location|$message|$data")
            } catch (_: Throwable) {
                // JVM unit tests — no Android Log
            }
            try {
                File("C:/Users/ASUS/Stathis/debug-b7147e.log").appendText(payload + "\n")
            } catch (_: Throwable) {
            }
            executor.execute {
                post(ENDPOINT, payload) || post(EMULATOR_ENDPOINT, payload)
            }
        } catch (_: Throwable) {
            // never break exercise detection for debug logging
        }
    }

    private fun post(url: String, payload: String): Boolean {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1500
                readTimeout = 1500
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Debug-Session-Id", SESSION)
            }
            BufferedOutputStream(conn.outputStream).use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
