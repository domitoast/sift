package dev.sift.summarize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.sift.config.GeminiProperties;
import dev.sift.fetch.FailureType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiSummarizerTest {
    private static final String KEY = "test-api-key";

    private HttpServer server;
    private GeminiSummarizer summarizer;

    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("★ 宣告自己是 GEMINI——SummarizerRegistry 靠這個建表")
    void provider_shouldBeGemini() {
        respond(200, okBody("x"));

        assertThat(summarizer.provider()).isEqualTo(LlmProvider.GEMINI);
    }

    @Test
    @DisplayName("★★ 從 steps 裡挑出 model_output，不是挑第一個")
    void summarize_shouldExtractModelOutput() {
        respond(200, """
                {
                  "status": "completed",
                  "steps": [
                    { "type": "thought", "signature": "abc123" },
                    { "type": "model_output",
                      "content": [ { "type": "text", "text": "這是模型寫的摘要。" } ] }
                  ]
                }
                """);

        assertThat(summarize()).isEqualTo("這是模型寫的摘要。");
    }

    @Test
    @DisplayName("★ API key 放在標頭，不在網址裡")
    void summarize_shouldSendKeyInHeader() {
        respond(200, okBody("摘要"));

        summarize();

        assertThat(lastApiKeyHeader.get()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("★★ 內文裡的引號與換行不會產生壞掉的 JSON")
    void summarize_shouldEscapeJson() {
        respond(200, okBody("摘要"));

        summarizer.summarize(
                "他說「\"hello\"」\\ 然後",
                "第一行\n第二行\t有 tab \"引號\"",
                KEY);

        assertThatCode(lastRequestBody.get());
    }

    @Test
    @DisplayName("★★ 429 限流 → TRANSIENT（等一下會恢復）")
    void summarize_rateLimited_shouldBeTransient() {
        respond(429, """
                { "error": { "code": 429, "message": "Resource exhausted" } }
                """);

        assertThatThrownBy(this::summarize)
                .isInstanceOf(SummarizationException.class)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.TRANSIENT);
    }

    @Test
    @DisplayName("★★ 401 key 無效 → PERMANENT（重試一百次還是無效）")
    void summarize_unauthorized_shouldBePermanent() {
        respond(401, """
                { "error": { "code": 401, "message": "API key not valid" } }
                """);

        assertThatThrownBy(this::summarize)
                .isInstanceOf(SummarizationException.class)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }

    @Test
    @DisplayName("★ 503 對方的伺服器問題 → TRANSIENT")
    void summarize_serverError_shouldBeTransient() {
        respond(503, "{}");

        assertThatThrownBy(this::summarize)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.TRANSIENT);
    }

    @Test
    @DisplayName("★★ 沒列到的狀態碼一律當 TRANSIENT——兩種猜錯的代價不對稱")
    void summarize_unknownStatus_shouldDefaultToTransient() {
        respond(418, "{}");

        assertThatThrownBy(this::summarize)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.TRANSIENT);
    }

    @Test
    @DisplayName("★ 回 200 但沒有文字（被安全過濾擋下）→ PERMANENT")
    void summarize_emptyOutput_shouldBePermanent() {
        respond(200, """
                { "status": "completed", "steps": [ { "type": "thought" } ] }
                """);

        assertThatThrownBy(this::summarize)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }

    @Test
    @DisplayName("★ 逾時 → TRANSIENT，而且不會卡住整批")
    void summarize_timeout_shouldBeTransient() throws IOException {
        server.createContext("/v1beta/interactions", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            send(exchange, 200, okBody("太慢了"));
        });

        buildSummarizer(Duration.ofSeconds(1));

        assertThatThrownBy(this::summarize)
                .isInstanceOf(SummarizationException.class)
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.TRANSIENT);
    }

    @Test
    @DisplayName("★★ 沒有 API key → PERMANENT，而且不會發出任何請求")
    void summarize_withoutKey_shouldFailBeforeCallingApi() {
        respond(200, okBody("不該被呼叫"));

        assertThatThrownBy(() -> summarizer.summarize("標題", "內文", "  "))
                .extracting(e -> ((SummarizationException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);

        assertThat(lastRequestBody.get()).isNull();
    }

    private void respond(int status, String body) {
        server.createContext("/v1beta/interactions", exchange -> {
            lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            lastRequestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, status, body);
        });

        buildSummarizer(Duration.ofSeconds(5));
    }

    private void buildSummarizer(Duration timeout) {
        summarizer = new GeminiSummarizer(
                new ObjectMapper(),
                new GeminiProperties(
                        "http://localhost:" + server.getAddress().getPort() + "/v1beta/interactions",
                        "gemini-test",
                        timeout,
                        6000));
    }

    private String summarize() {
        return summarizer.summarize("測試標題", "測試內文", KEY);
    }

    private static String okBody(String text) {
        return """
                { "steps": [ { "type": "model_output",
                  "content": [ { "type": "text", "text": "%s" } ] } ] }
                """.formatted(text);
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange,
                             int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void assertThatCode(String json) {
        try {
            assertThat(new ObjectMapper().readTree(json).path("input").asText())
                    .contains("引號");
        } catch (Exception e) {
            throw new AssertionError("送出去的不是合法的 JSON：" + json, e);
        }
    }
}
