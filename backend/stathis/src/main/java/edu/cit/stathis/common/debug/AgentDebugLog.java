package edu.cit.stathis.common.debug;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Session-scoped NDJSON debug logger for agent investigations.
 * Writes to workspace debug-b7147e.log (folded call sites only).
 */
public final class AgentDebugLog {
    private static final Path LOG_PATH = Path.of("C:\\Users\\ASUS\\Stathis\\debug-b7147e.log");

    private AgentDebugLog() {}

    public static void log(String hypothesisId, String location, String message, Map<String, ?> data) {
        // #region agent log
        try {
            StringBuilder dataJson = new StringBuilder("{");
            if (data != null) {
                boolean first = true;
                for (Map.Entry<String, ?> e : data.entrySet()) {
                    if (!first) dataJson.append(',');
                    first = false;
                    dataJson.append('"').append(escape(e.getKey())).append("\":");
                    Object v = e.getValue();
                    if (v == null) dataJson.append("null");
                    else if (v instanceof Number || v instanceof Boolean) dataJson.append(v);
                    else dataJson.append('"').append(escape(String.valueOf(v))).append('"');
                }
            }
            dataJson.append('}');
            String line = "{\"sessionId\":\"b7147e\",\"hypothesisId\":\"" + escape(hypothesisId)
                    + "\",\"location\":\"" + escape(location)
                    + "\",\"message\":\"" + escape(message)
                    + "\",\"data\":" + dataJson
                    + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            Files.writeString(LOG_PATH, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // never break request path for debug logging
        }
        // #endregion
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
