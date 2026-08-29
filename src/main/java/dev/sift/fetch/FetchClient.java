package dev.sift.fetch;

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

    /**
     * 連線 timeout：5 秒內連不上就放棄。
     *
     * <p>不設會怎樣：對方的伺服器「不回應也不拒絕」（例如防火牆把封包丟掉），
     * 這個執行緒會一直等下去。10 個這種來源就吃掉 10 個執行緒，
     * <b>整個服務被一個沒人用的部落格拖垮。</b>
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 整個請求的 timeout：10 秒內沒拿完就放棄。
     *
     * <p>只設 connect timeout 不夠——對方可以「連得上，但每秒只吐一個位元組」，
     * 連線是成功的，資料永遠傳不完。這種攻擊有名字，叫 slowloris。
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 回應大小上限：5 MB。
     *
     * <p>一份正常的 RSS 大約 20–200 KB，5 MB 已經非常寬鬆。
     *
     * <p>不設會怎樣：對方回一個 10 GB 的檔案，我們照單全收，記憶體直接爆掉。
     * 注意<b>不能等下載完再檢查大小</b>——那時候傷害已經造成了。
     */
    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    /**
     * HttpClient 建立一次就重複使用。
     *
     * <p>每次請求都 new 一個的話，連線池、執行緒池全部重來，很浪費。
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            // 不跟隨 redirect：檢查過的網址和實際連到的網址必須是同一個
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

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
                .timeout(REQUEST_TIMEOUT)
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

        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                log.warn("拒絕連線到內部位址 host={} ip={}", host, address.getHostAddress());
                throw new FeedFetchException(FailureType.PERMANENT, "不允許連線到內部位址");
            }
        }
    }

    private boolean isInternal(InetAddress address) {
        return address.isLoopbackAddress()      // 127.0.0.1、::1
                || address.isLinkLocalAddress()  // 169.254.x.x ← 雲端 metadata
                || address.isSiteLocalAddress()  // 10.x、172.16-31.x、192.168.x
                || address.isAnyLocalAddress()   // 0.0.0.0
                || address.isMulticastAddress();
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
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);

            if (bytes.length > MAX_BODY_BYTES) {
                throw new FeedFetchException(FailureType.PERMANENT,
                        "回應超過 %d MB 上限".formatted(MAX_BODY_BYTES / 1024 / 1024));
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
