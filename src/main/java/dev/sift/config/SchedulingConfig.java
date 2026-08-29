package dev.sift.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 開啟排程功能。
 *
 * <p>{@code @EnableScheduling} 讓 Spring 在啟動時去掃描所有標了
 * {@code @Scheduled} 的方法，並幫它們安排執行緒定期執行。
 * <b>沒有這個註解，{@code @Scheduled} 完全不會生效，而且不會有任何警告。</b>
 *
 * <p><b>為什麼要 {@code @ConditionalOnProperty}</b>：
 * 排程一旦啟用，跑 {@code mvnw test} 時它也會啟動，
 * 然後在測試中途真的對外網發出 HTTP 請求——
 * 測試會變慢、變得不穩定，而且會打擾別人的伺服器。
 *
 * <p>因此 {@code application-test.yml} 把 {@code sift.scheduling.enabled} 設為 false，
 * 整個排程機制在測試環境下不會被建立。
 *
 * <p>{@code matchIfMissing = true} 的意思是「沒設定就當作 true」——
 * 正式環境不需要特地去開它。
 *
 * <p><b>這個開關管的是所有排程</b>（抓取、refresh token 清除），
 * 因此屬性名稱用 {@code sift.scheduling} 而不是 {@code sift.fetch}。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sift.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
