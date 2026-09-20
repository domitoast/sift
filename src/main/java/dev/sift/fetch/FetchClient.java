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
 * Outbound HTTP with timeouts, a response size cap and SSRF protection.
 */
@Component
public class FetchClient {
    private static final Logger log = LoggerFactory.getLogger(FetchClient.class);

    private final FetchProperties properties;
    private final InternalAddressChecker addressChecker;

    private final HttpClient httpClient;

    public FetchClient(FetchProperties properties, InternalAddressChecker addressChecker) {
        this.properties = properties;
        this.addressChecker = addressChecker;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())

                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        if (properties.allowInternalAddress()) {
            log.warn("========================================================");
            log.warn("  內部位址檢查已停用（sift.fetch.allow-internal-address）");
            log.warn("  僅供測試使用。正式環境必須設為 false，否則存在 SSRF 風險。");
            log.warn("========================================================");
        }
    }

    public String fetch(String url) {
        URI uri = toUri(url);
        assertNotInternalAddress(uri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(properties.requestTimeout())

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
            throw new FeedFetchException(FailureType.TRANSIENT, "連線失敗：" + e.getMessage());
        } catch (InterruptedException e) {
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

    private void assertNotInternalAddress(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new FeedFetchException(FailureType.PERMANENT, "網址沒有主機名稱");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new FeedFetchException(FailureType.PERMANENT, "找不到主機：" + host);
        }

        if (properties.allowInternalAddress()) {
            return;
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
            throw new FeedFetchException(FailureType.TRANSIENT, "對方伺服器錯誤：" + statusCode);
        }

        throw new FeedFetchException(FailureType.PERMANENT, "網址無法存取：" + statusCode);
    }

    private String readBounded(HttpResponse<InputStream> response) throws IOException {
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(properties.maxBodyBytes() + 1);

            if (bytes.length > properties.maxBodyBytes()) {
                throw new FeedFetchException(FailureType.PERMANENT,
                        "回應超過 %d bytes 上限".formatted(properties.maxBodyBytes()));
            }

            return new String(bytes, charsetOf(response));
        }
    }

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
