package dev.sift.summarize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * exponential backoff 的 unit test。
 *
 * <p><b>這個類別的輸出有隨機成分（jitter），所以斷言不能寫死。</b>
 * 只能驗證「落在合理範圍內」——這是測試有隨機性的程式碼時的標準做法。
 */
class RetryPolicyTest {

    /** 基礎 60 秒、最多 3 次、±20% jitter。與正式環境的預設值相同。 */
    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(60), 0.2);

    /** 沒有 jitter 的版本，用來驗證「翻倍」這件事本身。 */
    private final RetryPolicy noJitter = new RetryPolicy(3, Duration.ofSeconds(60), 0.0);

    private long secondsUntil(Instant instant) {
        return Duration.between(Instant.now(), instant).toSeconds();
    }

    // ---------- 要不要重試 ----------

    @Test
    @DisplayName("還沒用完次數 → 可以重試")
    void shouldRetry_underLimit_shouldBeTrue() {

        assertThat(policy.shouldRetry(0)).isTrue();
        assertThat(policy.shouldRetry(1)).isTrue();
        assertThat(policy.shouldRetry(2)).isTrue();
    }

    @Test
    @DisplayName("★★ 用完次數 → 不再重試")
    void shouldRetry_atLimit_shouldBeFalse() {

        /*
         * 這一題保護的是「不會無限重試」。
         *
         * 少了上限，一個永遠會失敗的項目會被無限期重試下去——
         * 每一次都花錢，而且永遠不會有人發現它壞了。
         */
        assertThat(policy.shouldRetry(3)).isFalse();
        assertThat(policy.shouldRetry(99)).isFalse();
    }

    // ---------- 間隔要翻倍 ----------

    @Test
    @DisplayName("★★ 間隔逐次翻倍：60 → 120 → 240 秒")
    void nextRetryAt_shouldDouble() {

        /*
         * 用 jitterRatio = 0 的版本，才驗證得了「翻倍」本身。
         * 有 jitter 的話每次結果都不同，斷言寫不出來。
         *
         * 這是測試有隨機性的程式碼的常見手法：
         * 把隨機的部分關掉，先驗證確定的邏輯。
         */
        assertThat(secondsUntil(noJitter.nextRetryAt(0))).isBetween(59L, 60L);
        assertThat(secondsUntil(noJitter.nextRetryAt(1))).isBetween(119L, 120L);
        assertThat(secondsUntil(noJitter.nextRetryAt(2))).isBetween(239L, 240L);
    }

    // ---------- jitter ----------

    @Test
    @DisplayName("★ jitter 讓等待時間落在 ±20% 之內")
    void nextRetryAt_withJitter_shouldStayInRange() {

        // 60 秒 ±20% → 48 ~ 72 秒
        for (int i = 0; i < 50; i++) {
            assertThat(secondsUntil(policy.nextRetryAt(0))).isBetween(47L, 72L);
        }
    }

    @Test
    @DisplayName("★★ 同樣的輸入要產生不同的等待時間——這正是 jitter 的目的")
    void nextRetryAt_withJitter_shouldVary() {

        /*
         * 沒有 jitter 會怎樣：100 篇文章在同一秒失敗，
         * 它們的下次重試時間也會是同一秒——尖峰只是往後移了一分鐘，
         * 沒有消失。這叫 thundering herd。
         *
         * 這一題連叫 20 次，要求結果不能全部一樣。
         */
        long distinct = java.util.stream.IntStream.range(0, 20)
                .mapToLong(i -> policy.nextRetryAt(0).toEpochMilli())
                .distinct()
                .count();

        assertThat(distinct).isGreaterThan(1);
    }
}
