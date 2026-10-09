package com.example.apex;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Minimal client for the Anthropic Messages API. */
public final class ClaudeClient {

    private static final String URL_STR = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-5-5";

    private ClaudeClient() {}

    /**
     * @param history list of {role, content} pairs, roles "user" / "assistant",
     *                must start with "user" and alternate.
     * @return the assistant's text reply
     */
    public static String ask(String apiKey, String system, List<String[]> history) throws Exception {
        JSONArray messages = new JSONArray();
        for (String[] m : history) {
            messages.put(new JSONObject().put("role", m[0]).put("content", m[1]));
        }

        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 400)
                .put("system", system)
                .put("messages", messages);

        HttpURLConnection conn = (HttpURLConnection) new URL(URL_STR).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty("x-api-key", apiKey);
        conn.setRequestProperty("anthropic-version", "2023-06-01");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String raw = read(is);
        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + raw);
        }

        JSONArray content = new JSONObject(raw).getJSONArray("content");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.getJSONObject(i);
            if ("text".equals(part.optString("type"))) {
                sb.append(part.getString("text"));
            }
        }
        return sb.toString().trim();
    }

    private static String read(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
