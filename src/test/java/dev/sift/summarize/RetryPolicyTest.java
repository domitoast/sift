package dev.sift.summarize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(60), 0.2);

    private final RetryPolicy noJitter = new RetryPolicy(3, Duration.ofSeconds(60), 0.0);

    private long secondsUntil(Instant instant) {
        return Duration.between(Instant.now(), instant).toSeconds();
    }

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
        assertThat(policy.shouldRetry(3)).isFalse();
        assertThat(policy.shouldRetry(99)).isFalse();
    }

    @Test
    @DisplayName("★★ 間隔逐次翻倍：60 → 120 → 240 秒")
    void nextRetryAt_shouldDouble() {
        assertThat(secondsUntil(noJitter.nextRetryAt(0))).isBetween(59L, 60L);
        assertThat(secondsUntil(noJitter.nextRetryAt(1))).isBetween(119L, 120L);
        assertThat(secondsUntil(noJitter.nextRetryAt(2))).isBetween(239L, 240L);
    }

    @Test
    @DisplayName("★ jitter 讓等待時間落在 ±20% 之內")
    void nextRetryAt_withJitter_shouldStayInRange() {
        for (int i = 0; i < 50; i++) {
            assertThat(secondsUntil(policy.nextRetryAt(0))).isBetween(47L, 72L);
        }
    }

    @Test
    @DisplayName("★★ 同樣的輸入要產生不同的等待時間——這正是 jitter 的目的")
    void nextRetryAt_withJitter_shouldVary() {
        long distinct = java.util.stream.IntStream.range(0, 20)
                .mapToLong(i -> policy.nextRetryAt(0).toEpochMilli())
                .distinct()
                .count();

        assertThat(distinct).isGreaterThan(1);
    }
}
