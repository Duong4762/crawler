package com.demo.crawlerproject.service;

import com.demo.crawlerproject.config.SelectorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LlmService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String API_KEY = "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";
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build();

    public SelectorConfig generateSelectorConfig(String html) {
        log.info("Generate selector config");
        String prompt = buildPrompt(html);
        try {
            String responseText = callLLMApi(prompt);
            String jsonOnly = extractJsonBlock(responseText);
            log.info("Result from AI: {}", jsonOnly);
            return objectMapper.readValue(jsonOnly, SelectorConfig.class);
        } catch (Exception e) {
            log.error("Failed to get selector config from LLM", e);
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

    private String callLLMApi(String prompt) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(new ChatRequest(prompt));

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            String responseBody = response.body().string();

            JsonNode root = objectMapper.readTree(responseBody);
            return root.at("/choices/0/message/content").asText();
        }
    }

    private String extractJsonBlock(String text) {
        int start = text.indexOf('{');
        if (start == -1) {
            throw new IllegalArgumentException("No JSON block found in LLM response");
        }

        int bracesCount = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                bracesCount++;
            } else if (c == '}') {
                bracesCount--;
                if (bracesCount == 0) {
                    // Trả về substring từ dấu '{' đầu đến dấu '}' cân bằng cuối
                    return text.substring(start, i + 1);
                }
            }
        }

        throw new IllegalArgumentException("No balanced JSON block found in LLM response");
    }

    static class ChatRequest {
        public String model = "mistralai/mistral-small-3.1-24b-instruct:free";
        public Message[] messages;

        public ChatRequest(String prompt) {
            this.messages = new Message[]{new Message("user", prompt)};
        }
    }

    static class Message {
        public String role;
        public Content[] content;

        public Message(String role, String prompt) {
            this.role = role;
            this.content = new Content[]{new Content("text", prompt)};
        }
    }

    static class Content {
        public String type;
        public String text;

        public Content(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }

}
