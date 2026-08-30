package dev.sift.fetch;

import dev.sift.config.FetchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 去把一個網址的內容抓回來。整個專案裡唯一會對外發出 HTTP 請求的地方。
 *
 * <p><b>這是攻擊面。</b> 網址是使用者填的，因此每一項防護都寫在這裡：
 *
 * <ul>
 *   <li>不連內部位址（SSRF）</li>
 *   <li>不跟隨 redirect</li>
 *   <li>連線與讀取都有 timeout</li>
 *   <li>回應大小有上限</li>
 * </ul>
 *
 * <p>解析在 {@link FeedParser}——那裡不碰網路，所以可以用 unit test 測。
 * 這裡碰網路，所以只能手動驗證。<b>把不穩定的部分縮到最小，就是這個拆法的目的。</b>
 */
@Component
public class FetchClient {

    private static final Logger log = LoggerFactory.getLogger(FetchClient.class);

    private final FetchProperties properties;
    private final InternalAddressChecker addressChecker;

    /**
     * HttpClient 建立一次就重複使用。
     *
     * <p>每次請求都 new 一個的話，連線池、執行緒池全部重來，很浪費。
     */
    private final HttpClient httpClient;

    public FetchClient(FetchProperties properties, InternalAddressChecker addressChecker) {

        this.properties = properties;
        this.addressChecker = addressChecker;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                // 不跟隨 redirect：檢查過的網址和實際連到的網址必須是同一個
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        /*
         * 這個警告是「選項 C」的核心。
         *
         * allowInternalAddress 是為了測試而存在的——測試用的假伺服器
         * 只能架在 localhost，而 localhost 正是我們要擋的東西。
         *
         * 但一個「可以關掉安全檢查」的開關本身就是風險：
         * 有人在正式環境誤設成 true，防護就完全消失，而且悄無聲息。
         *
         * 所以規則不是「不准關」，是「關了要非常明顯」——
         * 啟動 log 的第一頁就會看到這一行。
         */
        if (properties.allowInternalAddress()) {
            log.warn("========================================================");
            log.warn("  內部位址檢查已停用（sift.fetch.allow-internal-address）");
            log.warn("  僅供測試使用。正式環境必須設為 false，否則存在 SSRF 風險。");
            log.warn("========================================================");
        }
    }

    /**
     * @param url 要抓的網址
     * @return 回應內容的原始文字，交給 {@link FeedParser} 解析
     * @throws FeedFetchException 任何失敗，並附上該重試或不該重試的分類
     */
    public String fetch(String url) {

        URI uri = toUri(url);
        assertNotInternalAddress(uri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(properties.requestTimeout())
                // 表明身分是禮貌，也讓對方在需要時能封鎖我們而不是整段 IP
                .header("User-Agent", "Sift/0.1 (+https://github.com/domitoast/sift)")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            assertSuccessStatus(response.statusCode());

            String body = readBounded(response);

            log.info("抓取成功 url={} 狀態={} 大小={} bytes", url, response.statusCode(), body.length());

            return body;

        } catch (IOException e) {
            // 連線被拒、timeout、DNS 暫時查不到 —— 明天再試很可能就好了
            throw new FeedFetchException(FailureType.TRANSIENT, "連線失敗：" + e.getMessage());

        } catch (InterruptedException e) {
            // 執行緒被中斷時，一定要把中斷旗標設回去，否則上層永遠不知道
            Thread.currentThread().interrupt();
            throw new FeedFetchException(FailureType.TRANSIENT, "抓取被中斷");
        }
    }

    private URI toUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new FeedFetchException(FailureType.PERMANENT, "網址格式不正確");
        }
    }

    /**
     * SSRF 防護：不准連到內部位址。
     *
     * <p>Day 13 的 {@code @Pattern("^https?://.+")} 只擋掉了協定。
     * 下面這些全都是 http 開頭，全都通過那個檢查：
     *
     * <pre>
     * http://169.254.169.254/latest/meta-data/   雲端主機的憑證
     * http://localhost:5432/                     我們自己的資料庫
     * http://192.168.1.1/                        內網其他機器
     * </pre>
     *
     * <p><b>檢查所有解析出來的 IP，不是只檢查第一個</b>——
     * 一個網域可以回答多個位址，只看第一個就能被繞過。
     *
     * <p>⚠️ 已知限制：檢查與連線之間 DNS 可能換答案（DNS rebinding）。
     * 要完全堵住必須自己接管連線、強制連到剛才檢查過的那個 IP。
     */
    private void assertNotInternalAddress(URI uri) {

        String host = uri.getHost();
        if (host == null) {
            throw new FeedFetchException(FailureType.PERMANENT, "網址沒有主機名稱");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // 這個網域根本不存在，明天也不會存在
            throw new FeedFetchException(FailureType.PERMANENT, "找不到主機：" + host);
        }

        if (properties.allowInternalAddress()) {
            return;   // 僅測試環境。建構子已在啟動時印出 WARN
        }

        for (InetAddress address : addresses) {
            if (addressChecker.isInternal(address)) {
                log.warn("拒絕連線到內部位址 host={} ip={}", host, address.getHostAddress());
                throw new FeedFetchException(FailureType.PERMANENT, "不允許連線到內部位址");
            }
        }
    }

    private void assertSuccessStatus(int statusCode) {

        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        if (statusCode >= 300 && statusCode < 400) {
            throw new FeedFetchException(FailureType.PERMANENT,
                    "這個網址會轉址（%d），請直接填最終網址".formatted(statusCode));
        }

        if (statusCode >= 500) {
            // 對方自己壞了，明天可能就好了
            throw new FeedFetchException(FailureType.TRANSIENT, "對方伺服器錯誤：" + statusCode);
        }

        // 404、403 這類——網址本身有問題，重試幾次都一樣
        throw new FeedFetchException(FailureType.PERMANENT, "網址無法存取：" + statusCode);
    }

    /**
     * 讀取回應，但最多只讀 {@link #MAX_BODY_BYTES}。
     *
     * <p>用 {@code readNBytes} 而不是 {@code readAllBytes}：
     * 前者讀到上限就停，後者會把 10 GB 全部吃進記憶體才讓你發現太大。
     */
    private String readBounded(HttpResponse<InputStream> response) throws IOException {

        try (InputStream in = response.body()) {

            // 多讀 1 個 byte，用來判斷「是剛好到上限，還是被截斷了」
            byte[] bytes = in.readNBytes(properties.maxBodyBytes() + 1);

            if (bytes.length > properties.maxBodyBytes()) {
                throw new FeedFetchException(FailureType.PERMANENT,
                        "回應超過 %d bytes 上限".formatted(properties.maxBodyBytes()));
            }

            return new String(bytes, charsetOf(response));
        }
    }

    /**
     * 從 Content-Type 取編碼，沒有就預設 UTF-8。
     *
     * <p>⚠️ 已知限制：不看 XML 宣告裡的 {@code encoding="Big5"}。
     * 絕大多數 feed 是 UTF-8，少數非 UTF-8 且沒在 header 標示的會變亂碼。
     */
    private Charset charsetOf(HttpResponse<?> response) {

        return response.headers().firstValue("Content-Type")
                .filter(contentType -> contentType.contains("charset="))
                .map(contentType -> contentType.split("charset=")[1].trim().replace("\"", ""))
                .map(name -> {
                    try {
                        return Charset.forName(name);
                    } catch (Exception e) {
                        return StandardCharsets.UTF_8;
                    }
                })
                .orElse(StandardCharsets.UTF_8);
    }
}
