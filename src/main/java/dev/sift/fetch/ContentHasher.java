package dev.sift.fetch;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 計算一篇文章的「身分」——用來判斷「這是不是同一篇」。
 *
 * <h2>為什麼用內容的雜湊值，而不是網址</h2>
 *
 * ADR-004 的決定。同一篇文章的網址每天可能長得不一樣：
 *
 * <pre>
 * 今天  example.com/post?id=5
 * 明天  example.com/post?id=5&amp;utm_source=rss
 * </pre>
 *
 * 用網址比對會把它們當成兩篇不同的文章，於是重複存、重複付費做摘要。
 *
 * <p>另一個好處：比對 64 個字元的固定長度字串，索引效率比比對
 * 最長 1000 字元的網址好。
 *
 * <p><b>代價</b>：原文改一個錯字就會被視為新文章。
 * ADR-004 判斷「網址雜訊每天發生，改錯字一年幾次」，優先解決高頻問題。
 *
 * <p>這個類別不碰資料庫、不碰網路——字串進、字串出。
 */
@Component
public class ContentHasher {

    /**
     * @param title   標題
     * @param content 內文，可以是 null
     * @return 64 個字元的十六進位字串，對應資料庫的 {@code content_hash CHAR(64)}
     */
    public String hash(String title, String content) {

        /*
         * 用換行把兩段接起來，而不是直接相接。
         *
         * 直接相接的話，("ab", "c") 和 ("a", "bc") 會產生同一個雜湊值——
         * 兩篇不同的文章被當成同一篇，第二篇永遠存不進去，
         * 而且完全查不出原因。
         *
         * 這種問題有個名字，叫 hash collision by concatenation。
         * 加一個分隔字元就能避免。
         */
        String canonical = title + "\n" + (content == null ? "" : content);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 平台保證存在的演算法，這個分支實際上不會發生
            throw new IllegalStateException("找不到 SHA-256 演算法", e);
        }
    }
}
