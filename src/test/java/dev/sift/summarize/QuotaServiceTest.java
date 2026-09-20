package dev.sift.summarize;

import dev.sift.support.PostgresTestBase;
import dev.sift.user.User;
import dev.sift.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class QuotaServiceTest extends PostgresTestBase {
    private static final int TEST_LIMIT = 3;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private LlmUsageRepository llmUsageRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(
                new User("quota-" + System.nanoTime() + "@example.com", "hash")).getId();
    }

    @Test
    @DisplayName("★ 全新的使用者今天用了 0 次")
    void countToday_newUser_shouldBeZero() {
        assertThat(llmUsageRepository.countToday(userId)).isZero();
    }

    @Test
    @DisplayName("★★ 連續呼叫會累加，不會每次都建新的一列")
    void tryConsume_shouldAccumulate() {
        quotaService.tryConsume(userId);
        assertThat(llmUsageRepository.countToday(userId)).isEqualTo(1);

        quotaService.tryConsume(userId);
        assertThat(llmUsageRepository.countToday(userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("★★ 用完額度之後拒絕，而且不再增加計數")
    void tryConsume_overLimit_shouldRefuse() {
        for (int i = 0; i < TEST_LIMIT; i++) {
            assertThat(quotaService.tryConsume(userId)).isTrue();
        }

        assertThat(quotaService.tryConsume(userId)).isFalse();

        assertThat(llmUsageRepository.countToday(userId)).isEqualTo(TEST_LIMIT);
    }

    @Test
    @DisplayName("★ 不同使用者的額度互不影響")
    void tryConsume_differentUsers_shouldBeIndependent() {
        Long otherId = userRepository.save(
                new User("quota-other-" + System.nanoTime() + "@example.com", "hash")).getId();

        for (int i = 0; i < TEST_LIMIT; i++) {
            quotaService.tryConsume(userId);
        }

        assertThat(quotaService.tryConsume(userId)).isFalse();
        assertThat(quotaService.tryConsume(otherId)).isTrue();
    }
}
