package dev.sift.summarize;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 決定「還要不要重試」以及「下次什麼時候重試」。
 *
 * <h2>為什麼間隔要逐次翻倍（exponential backoff）</h2>
 *
 * 對方回 429 的意思是「你打太快了」。若立刻重試，等於用打得更快來回應——
 * 把對方打得更慘，而且自己的執行緒也卡在迴圈裡。
 *
 * <p>間隔翻倍的效果是：<b>對方越修不好，我們打得越少。</b>
 *
 * <pre>
 * 第 1 次失敗 → 等  60 秒
 * 第 2 次失敗 → 等 120 秒
 * 第 3 次失敗 → 等 240 秒
 * 第 4 次     → 放棄
 * </pre>
 *
 * <h2>為什麼要加 jitter（抖動）</h2>
 *
 * 100 篇文章若在同一秒失敗，它們的下次重試時間也會是同一秒——
 * 尖峰只是往後移了一分鐘，沒有消失。這叫 thundering herd。
 *
 * <p>在等待時間上加 ±20% 的隨機，它們就散開了。
 *
 * <h2>這個類別不碰資料庫、不碰網路</h2>
 *
 * 純粹的計算，所以測試是 unit test。
 * 但「隨機」讓斷言不能寫死——只能驗證範圍。
 */
@Component
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration baseDelay;
    private final double jitterRatio;

    public RetryPolicy(
            @Value("${sift.summarize.retry.max-attempts}") int maxAttempts,
            @Value("${sift.summarize.retry.base-delay}") Duration baseDelay,
            @Value("${sift.summarize.retry.jitter-ratio}") double jitterRatio) {

        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.jitterRatio = jitterRatio;
    }

    /**
     * 還值不值得再試一次。
     *
     * @param currentRetryCount 目前已經試過幾次
     */
    public boolean shouldRetry(int currentRetryCount) {
        return currentRetryCount < maxAttempts;
    }

    /**
     * 算出下次可以重試的時間點。
     *
     * <p><b>回傳絕對時間而不是「還要等幾秒」</b>：後者需要有人一直在數，
     * 服務重啟就忘了。絕對時間存進資料庫之後，重啟照樣有效。
     * V1 的 {@code next_retry_at} 欄位註解在 Day 3 就寫下這個理由。
     *
     * @param currentRetryCount 目前已經試過幾次（0 代表這是第一次失敗）
     */
    public Instant nextRetryAt(int currentRetryCount) {

        // 60 秒 → 120 秒 → 240 秒
        long baseMillis = baseDelay.toMillis() * (1L << currentRetryCount);

        /*
         * 加 jitter：在 base 的 ±jitterRatio 範圍內隨機。
         *
         * ThreadLocalRandom 而不是 new Random()：多執行緒下效率較好，
         * 而且不需要自己管理 seed。
         *
         * 這裡不需要 SecureRandom——jitter 只是為了打散時間點，
         * 不是安全用途。用 SecureRandom 只是白白變慢。
         *
         * ⚠️ jitterRatio = 0 必須特別處理：
         * nextDouble(0.0, 0.0) 的下界等於上界，Java 會丟
         * IllegalArgumentException: bound must be greater than origin。
         *
         * 0 是合法的設定值（代表「不要抖動」），不該讓它爆掉。
         */
        double factor = jitterRatio <= 0
                ? 1.0
                : 1.0 + ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);

        long delayMillis = Math.round(baseMillis * factor);

        return Instant.now().plusMillis(delayMillis);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
