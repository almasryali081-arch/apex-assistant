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

/** Minimal client for the Google Gemini generateContent API. */
public final class GeminiClient {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private GeminiClient() {}

    /**
     * @param history list of {role, content} pairs, roles "user" / "assistant".
     * @return the model's text reply
     */
    public static String ask(String apiKey, String model, String system, List<String[]> history)
            throws Exception {
        JSONArray contents = new JSONArray();
        for (String[] m : history) {
            String role = "assistant".equals(m[0]) ? "model" : "user";
            contents.put(new JSONObject()
                    .put("role", role)
                    .put("parts", new JSONArray().put(new JSONObject().put("text", m[1]))));
        }

        JSONObject body = new JSONObject()
                .put("systemInstruction", new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", new JSONObject().put("maxOutputTokens", 1024));

        HttpURLConnection conn =
                (HttpURLConnection) new URL(BASE + model + ":generateContent").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty("x-goog-api-key", apiKey);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String raw = read(is);
        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + raw);
        }

        JSONObject root = new JSONObject(raw);
        JSONArray candidates = root.optJSONArray("candidates");
        StringBuilder sb = new StringBuilder();
        if (candidates != null && candidates.length() > 0) {
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    sb.append(parts.getJSONObject(i).optString("text", ""));
                }
            }
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            throw new IOException("رد فارغ من Gemini: " + raw.substring(0, Math.min(200, raw.length())));
        }
        return text;
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
