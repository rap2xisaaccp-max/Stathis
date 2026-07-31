package citu.edu.stathis.mobile.core.debug

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Temporary debug-session NDJSON logger (session b7147e).
 * Prefer adb reverse: `adb reverse tcp:7316 tcp:7316` so 127.0.0.1 reaches the host ingest.
 */
object AgentDebugLog {
    private const val TAG = "AgentDebugLog"
    private const val ENDPOINT =
        "http://127.0.0.1:7316/ingest/495f4aba-74a7-432b-b062-a71e4ed7ed12"

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        // #region agent log
        thread(name = "agent-debug-log", isDaemon = true) {
            try {
                val payload = buildString {
                    append('{')
                    append("\"sessionId\":\"b7147e\",")
                    append("\"runId\":\"").append(runId).append("\",")
                    append("\"hypothesisId\":\"").append(escape(hypothesisId)).append("\",")
                    append("\"location\":\"").append(escape(location)).append("\",")
                    append("\"message\":\"").append(escape(message)).append("\",")
                    append("\"timestamp\":").append(System.currentTimeMillis()).append(',')
                    append("\"data\":{")
                    data.entries.forEachIndexed { index, (k, v) ->
                        if (index > 0) append(',')
                        append('"').append(escape(k)).append("\":")
                        append(jsonValue(v))
                    }
                    append("}}")
                }
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 1500
                    readTimeout = 1500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", "b7147e")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                conn.responseCode
                conn.disconnect()
            } catch (t: Throwable) {
                Log.w(TAG, "ingest failed: ${t.message}")
            }
        }
        Log.d(TAG, "[$hypothesisId] $location $message $data")
        // #endregion
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number, is Boolean -> v.toString()
        else -> "\"${escape(v.toString())}\""
    }
}
