package dev.sift.summarize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sift.config.GeminiProperties;
import dev.sift.fetch.FailureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Summarizer backed by the Gemini API, using each user's own key.
 */
@Component
public class GeminiSummarizer implements Summarizer {
    private static final Logger log = LoggerFactory.getLogger(GeminiSummarizer.class);

    private static final String INSTRUCTION = """
            你是一個技術文章摘要助手。請用繁體中文（台灣用語）寫出下面這篇文章的摘要。

            要求：
            1. 150 到 250 字
            2. 只寫摘要本身，不要加任何開場白、結語或標題
            3. 保留原文中的專有名詞與技術術語（例如 Kubernetes、latency），不要翻譯
            4. 如果內容不足以摘要，直接回覆「內容不足」四個字

            文章標題：%s

            文章內容：
            %s
            """;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeminiProperties properties;

    public GeminiSummarizer(ObjectMapper objectMapper, GeminiProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        log.info("使用 GeminiSummarizer，模型={} 逾時={}", properties.model(), properties.timeout());
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.GEMINI;
    }

    @Override
    public String summarize(String title, String content, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SummarizationException(FailureType.PERMANENT, "沒有可用的 API key");
        }

        if (title == null || title.isBlank()) {
            throw new SummarizationException(FailureType.PERMANENT, "文章沒有標題");
        }

        String body = buildRequestBody(title, content);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl()))
                    .header("Content-Type", "application/json")

                    .header("x-goog-api-key", apiKey)
                    .timeout(properties.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw classify(response.statusCode(), response.body());
            }

            return extractText(response.body());
        } catch (HttpTimeoutException e) {
            throw new SummarizationException(FailureType.TRANSIENT, "呼叫 Gemini 逾時");
        } catch (IOException e) {
            throw new SummarizationException(FailureType.TRANSIENT, "呼叫 Gemini 失敗：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SummarizationException(FailureType.TRANSIENT, "呼叫 Gemini 被中斷");
        }
    }

    private SummarizationException classify(int status, String body) {
        String detail = truncate(body, 300);

        return switch (status) {
            case 429 -> new SummarizationException(
                    FailureType.TRANSIENT, "Gemini 限流（429），稍後重試");

            case 500, 502, 503, 504 -> new SummarizationException(
                    FailureType.TRANSIENT, "Gemini 伺服器錯誤（" + status + "）");

            case 401, 403 -> new SummarizationException(
                    FailureType.PERMANENT, "Gemini 拒絕這把 API key（" + status + "）");

            case 400 -> new SummarizationException(
                    FailureType.PERMANENT, "Gemini 拒絕這個請求（400）：" + detail);

            default -> new SummarizationException(
                    FailureType.TRANSIENT, "Gemini 回傳非預期狀態（" + status + "）：" + detail);
        };
    }

    private String buildRequestBody(String title, String content) {
        String text = INSTRUCTION.formatted(
                title,
                truncate(content == null || content.isBlank() ? title : content,
                        properties.maxChars()));

        try {
            return objectMapper.writeValueAsString(
                    new GeminiRequest(properties.model(), text));
        } catch (Exception e) {
            throw new SummarizationException(FailureType.PERMANENT, "無法組出請求內容");
        }
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            StringBuilder text = new StringBuilder();

            for (JsonNode step : root.path("steps")) {
                if (!"model_output".equals(step.path("type").asText())) {
                    continue;
                }
                for (JsonNode part : step.path("content")) {
                    if ("text".equals(part.path("type").asText())) {
                        text.append(part.path("text").asText());
                    }
                }
            }

            String result = text.toString().trim();

            if (result.isEmpty()) {
                throw new SummarizationException(
                        FailureType.PERMANENT, "Gemini 回傳空內容（可能被安全過濾擋下）");
            }

            return result;
        } catch (SummarizationException e) {
            throw e;
        } catch (Exception e) {
            log.error("無法解析 Gemini 的回應：{}", truncate(responseBody, 500));
            throw new SummarizationException(FailureType.PERMANENT, "無法解析 Gemini 的回應");
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private record GeminiRequest(String model, String input) {
    }
}
