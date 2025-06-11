package com.demo.crawlerproject.service;

import com.demo.crawlerproject.config.SelectorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class LlmService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final String API_KEY = "AIzaSyDfVpFMY4MsnPtOTbyM91eHhoxHkWKrNMw"; // Thay bằng API key thật

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .callTimeout(1, TimeUnit.MINUTES)
            .build();

    public SelectorConfig generateSelectorConfig(String html) {
        log.info("Generate selector config using Gemini");
        String prompt = buildPrompt(html);
        try {
            String responseText = callGeminiApi(prompt);
            String jsonOnly = extractJsonBlock(responseText);
            log.info("Result from Gemini: {}", jsonOnly);
            return objectMapper.readValue(jsonOnly, SelectorConfig.class);
        } catch (Exception e) {
            log.error("Failed to get selector config from Gemini", e);
            return null;
        }
    }

    private String buildPrompt(String html) {
        return "Bạn là một chuyên gia phân tích cấu trúc trang web và CSS selector.\n" +
                "Dưới đây là mã HTML của một trang bài viết báo. Hãy phân tích và trích xuất các CSS selector chính xác, duy nhất, không bị lặp hoặc chồng lấn, cho các phần sau:\n" +
                "- Tiêu đề bài viết (title)\n" +
                "- Thời gian đăng bài (publishTime)\n" +
                "- Tác giả bài viết (author)\n" +
                "- Nội dung chính của bài viết (content) (ưu tiên selector cha bao phủ toàn bộ nội dung chính, tránh chọn nhiều selector nhỏ chồng lên nhau)\n" +
                "Trả về duy nhất một đối tượng JSON với 4 trường trên. Nếu không tìm thấy trường nào thì gán null.\n" +
                "Chỉ trả về JSON, không thêm chú thích hay văn bản giải thích.\n\n" +
                "HTML:\n" + html;
    }

    private String callGeminiApi(String prompt) throws IOException {
        String fullUrl = API_URL + "?key=" + API_KEY;

        // Xây dựng payload theo yêu cầu của Gemini API
        String payloadJson = objectMapper.writeValueAsString(new GeminiRequest(prompt));

        RequestBody body = RequestBody.create(payloadJson, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        long start = System.currentTimeMillis();

        try (Response response = httpClient.newCall(request).execute()) {
            long duration = System.currentTimeMillis() - start;
            log.info("Gemini API call took {} ms", duration);

            if (!response.isSuccessful()) {
                throw new IOException("Gemini API failed: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return root.at("/candidates/0/content/parts/0/text").asText();
        }
    }

    private String extractJsonBlock(String text) {
        int start = text.indexOf('{');
        if (start == -1) {
            throw new IllegalArgumentException("No JSON block found in Gemini response");
        }

        int bracesCount = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') bracesCount++;
            else if (c == '}') {
                bracesCount--;
                if (bracesCount == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("No balanced JSON block found in Gemini response");
    }

    // Payload định dạng đúng với Gemini
    static class GeminiRequest {
        public Content[] contents;

        public GeminiRequest(String prompt) {
            this.contents = new Content[]{
                    new Content(new Part(prompt))
            };
        }
    }

    static class Content {
        public Part[] parts;

        public Content(Part part) {
            this.parts = new Part[]{part};
        }
    }

    static class Part {
        public String text;

        public Part(String text) {
            this.text = text;
        }
    }
}
