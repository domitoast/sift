package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 把「使用者給的網址」變成「確定可用的 feed 網址」。
 *
 * <p>ADR-017 承諾的驗證流程，在建立來源時執行一次：
 *
 * <pre>
 * 使用者送出網址
 *    │
 *    ├─① 直接當 feed 解析  ──成功──> 就用這個網址
 *    │        │失敗（可能是 HTML）
 *    ├─② 當 HTML 找 &lt;link rel="alternate"&gt;  ──找不到──> ③ 放棄
 *    │        │找到
 *    └─④ 再抓一次那個網址，確認真的是 feed
 * </pre>
 *
 * <p><b>為什麼第 ④ 步不能省</b>：網頁宣告的 feed 網址不保證是對的，
 * 可能已經失效、可能指向一個 HTML 頁面。
 * 若不驗證就存進資料庫，錯誤會延後到明天排程才爆——
 * 而那時候使用者早就離開畫面了。
 *
 * <p><b>安全性</b>：第 ④ 步一樣經過 {@code FetchClient}，
 * 所以 autodiscovery 找到的網址也會被 SSRF 檢查。
 * 網頁可以宣告 {@code <link href="http://169.254.169.254/">}，
 * 但那一樣連不出去。
 */
@Service
public class FeedResolver {

    private static final Logger log = LoggerFactory.getLogger(FeedResolver.class);

    private final FetchClient fetchClient;
    private final FeedParser feedParser;
    private final FeedDiscoverer feedDiscoverer;

    public FeedResolver(FetchClient fetchClient,
                        FeedParser feedParser,
                        FeedDiscoverer feedDiscoverer) {
        this.fetchClient = fetchClient;
        this.feedParser = feedParser;
        this.feedDiscoverer = feedDiscoverer;
    }

    /**
     * @param url 使用者填的網址，可能是 feed，也可能是網站首頁
     * @return 確定可用的 feed 網址（可能與傳入的不同）
     * @throws FeedNotFoundException 這個網址既不是 feed，也找不到 feed
     * @throws FeedFetchException    連不上、404、超時等
     */
    public String resolve(String url) {

        String content = fetchClient.fetch(url);

        // ① 直接當 feed 試試看
        try {
            List<FetchedArticle> articles = feedParser.parse(content);
            log.info("網址本身就是 feed url={} 文章數={}", url, articles.size());
            return url;

        } catch (FeedParseException e) {
            log.debug("不是 feed，改用 autodiscovery url={}", url);
        }

        // ② 當成 HTML，找 <link rel="alternate">
        Optional<String> discovered = feedDiscoverer.discover(content, url);

        if (discovered.isEmpty()) {
            // ③ 找不到
            throw new FeedNotFoundException(url);
        }

        String feedUrl = discovered.get();
        log.info("autodiscovery 找到 feed 網址 原始={} feed={}", url, feedUrl);

        // ④ 驗證那個網址真的是 feed
        try {
            String feedContent = fetchClient.fetch(feedUrl);
            feedParser.parse(feedContent);

            return feedUrl;

        } catch (FeedParseException | FeedFetchException e) {
            /*
             * 網頁宣告了一個 feed 網址，但那個網址抓不到或不是 feed。
             *
             * 不能就這樣存進去——否則使用者以為訂閱成功，
             * 明天排程才發現抓不到，而那時候沒有人在看。
             */
            log.warn("autodiscovery 找到的網址無法使用 feed={} 原因={}", feedUrl, e.getMessage());
            throw new FeedNotFoundException(url);
        }
    }
}
