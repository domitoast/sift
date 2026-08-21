package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 相關設定，對應 application.yml 中的 {@code sift.jwt} 區段。
 *
 * <p>{@code @ConfigurationProperties} 把一段 YAML 設定綁定成 Java 物件。
 * 相較於在各處散落 {@code @Value("${sift.jwt.secret}")}：
 * <ul>
 *   <li>型別安全——寫錯名稱在啟動時就會發現，而非執行到那行才爆</li>
 *   <li>集中管理——所有 JWT 設定只出現在這一個檔案</li>
 *   <li>可注入——當成一般 Bean 注入，容易在測試中替換</li>
 * </ul>
 *
 * <p><b>relaxed binding（寬鬆綁定）</b>：YAML 寫
 * {@code access-token-ttl-minutes}，Java 欄位叫 {@code accessTokenTtlMinutes}，
 * Spring 會自動對應。連字號、底線、大小寫的差異都會被吸收。
 *
 * <p>使用 record 而非一般類別：這些設定啟動後不會改變，
 * 用不可變（immutable）的形式表達最貼切。
 * Spring Boot 支援以建構子綁定 record。
 *
 * @param secret                 Base64 編碼的簽章金鑰
 * @param accessTokenTtlMinutes  access token 有效分鐘數
 * @param refreshTokenTtlDays    refresh token 有效天數
 */
@ConfigurationProperties(prefix = "sift.jwt")
public record JwtProperties(
        String secret,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays
) {
}
