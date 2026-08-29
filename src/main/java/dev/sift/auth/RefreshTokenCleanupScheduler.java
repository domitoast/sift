package dev.sift.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 定期清掉過期的 refresh token。
 *
 * <p>ADR-010 決定了要清，但直到 Day 15 才真的排上去——
 * 在那之前每一次登入都會留下一列永遠不會被刪除的資料。
 *
 * <h2>為什麼用 cron 而不是 fixedDelay</h2>
 *
 * 兩者回答的是不同的問題：
 *
 * <table border="1">
 *   <tr><td>{@code fixedDelay}</td><td>「多久做一次」——上次做完後隔 N 毫秒</td></tr>
 *   <tr><td>{@code cron}</td><td>「什麼時候做」——每天凌晨三點</td></tr>
 * </table>
 *
 * <p>清理是後台維護工作，應該挑沒人在用的時段。
 * 用 {@code fixedDelay} 的話，執行時間會隨著每次啟動而漂移，
 * 最後可能落在流量最高的時候。
 */
@Component
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * 每天凌晨三點清理一次。
     *
     * <p>Spring 的 cron 是<b>六個欄位</b>，比 Linux 的多一個「秒」：
     *
     * <pre>
     * 秒 分 時 日 月 星期
     * 0  0  3  *  *  *     → 每天 03:00:00
     * </pre>
     *
     * <p>⚠️ 常見錯誤：照抄 Linux 的五欄位寫法（{@code "0 3 * * *"}），
     * 啟動時會直接失敗。這個錯誤好在會 fail fast，不會靜靜跑錯時間。
     *
     * <p>{@code @Transactional} 直接放在這裡是安全的——
     * 這個方法由 Spring 的排程器透過 proxy 呼叫，不是 self-invocation。
     * 而且整段只有一次資料庫操作，沒有網路 I/O，不會長時間佔住連線。
     */
    @Scheduled(cron = "${sift.cleanup.refresh-token-cron}")
    @Transactional
    public void deleteExpiredTokens() {

        int deleted = refreshTokenRepository.deleteExpired(Instant.now());

        log.info("清除過期的 refresh token，刪除 {} 列", deleted);
    }
}
