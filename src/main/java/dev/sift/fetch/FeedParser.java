package dev.sift.fetch;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 把一段 feed 的原始文字，轉成文章清單。
 *
 * <p><b>這個類別完全不碰網路,也不碰資料庫。</b>
 * 給它一個字串，它回傳一個 List。因此它的測試是 unit test，
 * 不需要外部網站配合，也永遠不會因為別人的伺服器掛掉而變紅。
 *
 * <p>抓取（不穩定的那半）在 {@code FetchClient}。
 */
@Component
public class FeedParser {

    private static final Logger log = LoggerFactory.getLogger(FeedParser.class);

    /**
     * @param rawFeed feed 的原始內容（RSS 或 Atom 都可以）
     * @return 文章清單。<b>沒有標題或沒有連結的項目會被略過</b>——
     *         沒有連結的文章我們沒辦法帶使用者去看原文，留著沒有用
     * @throws FeedParseException 內容不是合法的 feed
     */
    public List<FetchedArticle> parse(String rawFeed) {

        SyndFeedInput input = new SyndFeedInput();

        /*
         * 關掉 DOCTYPE。
         *
         * XML 的 DOCTYPE 可以寫這種東西：
         *   <!DOCTYPE x [ <!ENTITY e SYSTEM "file:///etc/passwd"> ]>
         * 解析器讀到就會去讀那個檔案，內容被塞進解析結果。
         *
         * 這叫 XXE（XML External Entity），
         * 和 Day 13 擋掉的 SSRF 是同一種問題——
         * 使用者給的東西，讓伺服器去存取它不該存取的地方。
         *
         * ⚠️ 這裡的網址是使用者填的，所以這條路是真的通的。
         *
         * Rome 的預設值本來就是 false，這行是寫給讀程式碼的人看的：
         * 「我知道有這個風險，而且我確認過它是關的。」
         */
        input.setAllowDoctypes(false);

        SyndFeed feed;
        try {
            feed = input.build(new StringReader(rawFeed));

        } catch (FeedException | IllegalArgumentException e) {
            /*
             * IllegalArgumentException 也要接：
             * Rome 遇到「這是合法 XML，但不是我認得的 feed 格式」時丟的是它，
             * 不是 FeedException。少接一個就會漏成 500。
             */
            throw new FeedParseException(e.getMessage());
        }

        List<FetchedArticle> articles = feed.getEntries().stream()
                .map(this::toArticle)
                .filter(article -> article != null)
                .toList();

        log.debug("解析完成 feed 標題={} 取得文章數={}", feed.getTitle(), articles.size());

        return articles;
    }

    /** @return 轉換結果，缺標題或缺連結時回傳 null（由呼叫端過濾掉） */
    private FetchedArticle toArticle(SyndEntry entry) {

        String title = trimToNull(entry.getTitle());
        String link = trimToNull(entry.getLink());

        if (title == null || link == null) {
            log.debug("略過不完整的項目 title={} link={}", title, link);
            return null;
        }

        return new FetchedArticle(title, link, extractContent(entry),
                toInstant(entry.getPublishedDate()));
    }

    /**
     * 取出文章內文，並清掉 HTML 標籤。
     *
     * <p>RSS 和 Atom 放的地方不一樣：
     *
     * <pre>{@code
     * RSS   <description>一段文字</description>   → getDescription()
     * Atom  <content>一段文字</content>           → getContents()
     * }</pre>
     *
     * <p>Atom 的 {@code <content>} 可以有多個（不同格式的同一份內容），
     * 取第一個就好。
     *
     * <p><b>回傳 null 是正常的</b>——很多 feed 只給標題和連結。
     */
    private String extractContent(SyndEntry entry) {

        // Atom 優先：<content> 通常是完整內文，<description> 只是摘要
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            String cleaned = stripHtml(entry.getContents().get(0).getValue());
            if (cleaned != null) {
                return cleaned;
            }
        }

        if (entry.getDescription() != null) {
            return stripHtml(entry.getDescription().getValue());
        }

        return null;
    }

    /**
     * 把 HTML 標籤清掉，只留純文字。
     *
     * <p><b>為什麼需要</b>：feed 的內文幾乎都是 HTML，直接存下來會變成這樣：
     *
     * <pre>{@code
     * <p>今天發表了新版本，<a href="https://...">詳見公告</a>。</p>
     * }</pre>
     *
     * <p>那段東西送去給 LLM 產生摘要，模型要花 token 讀那些標籤，
     * <b>而且是付費的 token</b>。清乾淨之後只剩：
     *
     * <pre>今天發表了新版本，詳見公告。</pre>
     *
     * <p>用 jsoup 而不是正規表示式——理由與 Day 16 的 autodiscovery 相同，
     * HTML 的寫法變化太多。而且 jsoup 會順便處理 {@code &amp;} 這類跳脫字元。
     *
     * <p>⚠️ <b>已知限制</b>：有些來源的 {@code <description>} 本來就不是內文。
     * 例如 Hacker News 放的是一個「Comments」連結，清乾淨之後只剩 {@code Comments}。
     * 那不是我們能修的——真的要全文必須去抓 {@code external_url} 的原始網頁，
     * 那是另一個工程（而且是新的 SSRF 面）。
     */
    private String stripHtml(String html) {

        String trimmed = trimToNull(html);
        if (trimmed == null) {
            return null;
        }

        return trimToNull(Jsoup.parse(trimmed).text());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Rome 回傳的是舊的 {@code java.util.Date}，轉成專案統一使用的 {@code Instant}。
     *
     * <p>很多 feed 根本不填發布時間，所以 null 是正常情況，不是錯誤。
     */
    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
