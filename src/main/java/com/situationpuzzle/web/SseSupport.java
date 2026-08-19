package com.situationpuzzle.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手動寫 SSE 並立即 flush，降低緩衝導致「整段結束才一次出現」的機率。
 */
public final class SseSupport {
    private SseSupport() {}

    public static void prepare(HttpServletResponse response) {
        response.setStatus(200);
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        try {
            response.setBufferSize(256);
        } catch (Exception ignored) {
            // ignore
        }
    }

    public static void send(HttpServletResponse response, ObjectMapper mapper, Map<String, Object> payload)
            throws IOException {
        PrintWriter w = response.getWriter();
        w.write("data:");
        w.write(mapper.writeValueAsString(payload));
        w.write("\n\n");
        w.flush();
        try {
            response.flushBuffer();
        } catch (Exception ignored) {
            // ignore
        }
    }

    public static Map<String, Object> event(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }
}
