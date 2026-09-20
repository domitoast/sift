package dev.sift.fetch;

import com.sun.net.httpserver.HttpServer;
import dev.sift.config.FetchProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchClientTest {
    private HttpServer server;
    private String baseUrl;

    private final FetchProperties properties = new FetchProperties(
            true,
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            1024,
            Duration.ofMinutes(10)
    );

    private final FetchClient client = new FetchClient(properties, new InternalAddressChecker());

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    @DisplayName("200 → 回傳內容")
    void fetch_ok_shouldReturnBody() {
        respond("/rss", 200, "<rss>hello</rss>");

        assertThat(client.fetch(baseUrl + "/rss")).isEqualTo("<rss>hello</rss>");
    }

    @Test
    @DisplayName("UTF-8 的中文不會變成亂碼")
    void fetch_utf8_shouldNotGarble() {
        respond("/zh", 200, "<rss>週會紀錄</rss>");

        assertThat(client.fetch(baseUrl + "/zh")).contains("週會紀錄");
    }

    @Test
    @DisplayName("★ 404 → PERMANENT（重試幾次都一樣）")
    void fetch_404_shouldBePermanent() {
        respond("/missing", 404, "not found");

        assertThatThrownBy(() -> client.fetch(baseUrl + "/missing"))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }

    @Test
    @DisplayName("★ 500 → TRANSIENT（對方自己壞了，明天可能就好）")
    void fetch_500_shouldBeTransient() {
        respond("/broken", 500, "server error");

        assertThatThrownBy(() -> client.fetch(baseUrl + "/broken"))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).getFailureType())
                .isEqualTo(FailureType.TRANSIENT);
    }

    @Test
    @DisplayName("★ 302 轉址 → PERMANENT，且不跟過去")
    void fetch_redirect_shouldBePermanentAndNotFollow() {
        respond("/moved", 302, "");

        assertThatThrownBy(() -> client.fetch(baseUrl + "/moved"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("轉址")
                .extracting(e -> ((FeedFetchException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }

    @Test
    @DisplayName("★★ 回應超過上限 → 拒絕，而不是全部讀進記憶體")
    void fetch_oversizedBody_shouldThrow() {
        respond("/huge", 200, "x".repeat(2000));

        assertThatThrownBy(() -> client.fetch(baseUrl + "/huge"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("上限");
    }

    @Test
    @DisplayName("★★ 對方回應太慢 → timeout，且分類為 TRANSIENT")
    void fetch_slowServer_shouldTimeout() {
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThatThrownBy(() -> client.fetch(baseUrl + "/slow"))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).getFailureType())
                .isEqualTo(FailureType.TRANSIENT);
    }

    @Test
    @DisplayName("★★ 關閉 allowInternalAddress 時，localhost 一律拒絕")
    void fetch_internalAddress_whenGuardEnabled_shouldThrow() {
        FetchProperties guarded = new FetchProperties(
                false, Duration.ofSeconds(2), Duration.ofSeconds(2), 1024,
                Duration.ofMinutes(10));

        FetchClient guardedClient = new FetchClient(guarded, new InternalAddressChecker());

        respond("/rss", 200, "<rss>hello</rss>");

        assertThatThrownBy(() -> guardedClient.fetch(baseUrl + "/rss"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("內部位址");
    }

    @Test
    @DisplayName("★ 網域不存在 → PERMANENT")
    void fetch_unknownHost_shouldBePermanent() {
        assertThatThrownBy(() -> client.fetch("https://this-host-does-not-exist-8f3k2.invalid/rss"))
                .isInstanceOf(FeedFetchException.class)
                .extracting(e -> ((FeedFetchException) e).getFailureType())
                .isEqualTo(FailureType.PERMANENT);
    }
}
