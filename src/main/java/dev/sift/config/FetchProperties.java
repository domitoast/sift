package dev.sift.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 抓取相關設定，對應 {@code application.yml} 的 {@code sift.fetch} 區段。
 *
 * <p>Day 15 這些值寫死在 {@code FetchClient} 裡。搬出來的兩個理由：
 *
 * <ol>
 *   <li><b>可測試</b>——測試可以把 timeout 設成 1 秒，
 *       不必為了驗證一個 timeout 而真的等 10 秒</li>
 *   <li><b>可調整</b>——正式環境要改就改設定檔，不必重新編譯</li>
 * </ol>
 *
 * @param allowInternalAddress 是否允許連到內部位址（loopback、內網、雲端 metadata）。
 *                             <b>正式環境必須是 false。</b>
 *                             設為 true 時 {@code FetchClient} 會在啟動時印出 WARN
 * @param connectTimeout       建立連線的上限
 * @param requestTimeout       整個請求的上限（含讀取回應）
 * @param maxBodyBytes         回應內容的大小上限
 */
@ConfigurationProperties(prefix = "sift.fetch")
public record FetchProperties(
        boolean allowInternalAddress,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxBodyBytes
) {
}
