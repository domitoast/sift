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

/**
 * FetchClient 的測試。
 *
 * <p><b>怎麼測一個「會連外網」的類別</b>：不要連外網。
 * 在 localhost 架一個我們自己控制的 HTTP 伺服器，
 * 想讓它回什麼就回什麼——404、超大回應、慢到 timeout 都能重現。
 *
 * <p>用的是 JDK 內建的 {@code com.sun.net.httpserver.HttpServer}，
 * 不需要任何新的依賴。
 *
 * <p><b>注意這裡的 FetchProperties 開了 allowInternalAddress</b>——
 * 因為假伺服器架在 localhost，而 localhost 正是正式環境要擋的東西。
 * SSRF 的判斷邏輯由 {@link InternalAddressCheckerTest} 獨立驗證，
 * 不依賴這個檔案。
 */
class FetchClientTest {

    private HttpServer server;
    private String baseUrl;

    private final FetchProperties properties = new FetchProperties(
            true,                    // allowInternalAddress：測試才可以
            Duration.ofSeconds(2),   // connectTimeout
            Duration.ofSeconds(2),   // requestTimeout
            1024                     // maxBodyBytes：1 KB，方便測上限
    );

    private final FetchClient client = new FetchClient(properties, new InternalAddressChecker());

    @BeforeEach
    void startServer() throws IOException {
        // port 給 0 代表「隨便挑一個沒被佔用的」，避免測試之間互相衝突
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** 註冊一個路徑，指定它要回傳的狀態碼與內容。 */
    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    // ---------- 正常情況 ----------

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

    // ---------- 失敗分類：這才是重點 ----------

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

    // ---------- 防護 ----------

    @Test
    @DisplayName("★★ 回應超過上限 → 拒絕，而不是全部讀進記憶體")
    void fetch_oversizedBody_shouldThrow() {

        /*
         * 上限設 1024，這裡回 2000 個字元。
         *
         * 重點不只是「會丟例外」，而是 readNBytes 讀到 1025 就停了——
         * 如果用 readAllBytes，對方回 10 GB 我們會先吃下 10 GB
         * 才發現太大，那時候記憶體已經爆了。
         */
        respond("/huge", 200, "x".repeat(2000));

        assertThatThrownBy(() -> client.fetch(baseUrl + "/huge"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("上限");
    }

    @Test
    @DisplayName("★★ 對方回應太慢 → timeout，且分類為 TRANSIENT")
    void fetch_slowServer_shouldTimeout() {

        /*
         * 模擬 slowloris：連得上、握手成功，但資料永遠傳不完。
         *
         * 若只設 connectTimeout 而沒設整個請求的 timeout，
         * 這個執行緒會被卡到天荒地老。
         *
         * requestTimeout 設 2 秒，所以這一題大約 2 秒就會結束。
         */
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

        /*
         * 這一題把開關關回 false，證明防護真的有作用。
         *
         * 沒有這一題的話，allowInternalAddress 這個開關本身
         * 就是一個「沒有人驗證過它關得起來」的後門。
         */
        FetchProperties guarded = new FetchProperties(
                false, Duration.ofSeconds(2), Duration.ofSeconds(2), 1024);

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
