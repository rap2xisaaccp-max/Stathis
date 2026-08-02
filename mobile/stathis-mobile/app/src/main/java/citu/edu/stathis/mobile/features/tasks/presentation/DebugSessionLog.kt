package citu.edu.stathis.mobile.features.tasks.presentation

import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Debug-session NDJSON logger (session b7147e). Posts to the Cursor debug ingest server.
 * Also mirrors to logcat. Safe no-op on failure.
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
        val payload = JSONObject()
            .put("sessionId", SESSION)
            .put("hypothesisId", hypothesisId)
            .put("location", location)
            .put("message", message)
            .put("timestamp", System.currentTimeMillis())
            .put("runId", runId)
            .put("data", JSONObject(data))
        android.util.Log.i("DbgSession_$SESSION", "$hypothesisId|$location|$message|$data")
        executor.execute {
            // Prefer 127.0.0.1 — works on device when `adb reverse tcp:7316 tcp:7316` is set.
            val ok = post(ENDPOINT, payload) || post(EMULATOR_ENDPOINT, payload)
            android.util.Log.i(
                "DbgSession_$SESSION",
                if (ok) "ingest_ok|$message" else "ingest_fail|$message"
            )
        }
    }

    private fun post(url: String, payload: JSONObject): Boolean {
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
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            android.util.Log.w("DbgSession_$SESSION", "ingest_error|$url|${e.javaClass.simpleName}")
            false
        }
    }
}
