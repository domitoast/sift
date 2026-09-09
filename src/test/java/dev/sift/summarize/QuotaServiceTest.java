package dev.sift.summarize;

import dev.sift.user.User;
import dev.sift.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 每日配額。
 *
 * <p><b>為什麼是整合測試</b>：要驗證的是 UPSERT
 * （{@code ON CONFLICT ... DO UPDATE}）真的有累加。
 * 把 repository mock 掉的話，測到的是「我叫 mock 回 5，它就回了 5」。
 *
 * <p>測試環境的上限設成 3（正式是 200），這樣不用呼叫 200 次才撞到牆。
 */
@ActiveProfiles("test")
@SpringBootTest
class QuotaServiceTest {

    /** 與 application-test.yml 的 sift.summarize.daily-quota 一致。 */
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

        /*
         * 這一題在保護 COALESCE。
         *
         * 沒有那個包裝的話，查不到紀錄時 SQL 回傳的是 null，
         * 而 null 賦值給 int 會直接爆 NullPointerException——
         * 而且是在「第一次使用」這個最常見的情境。
         */
        assertThat(llmUsageRepository.countToday(userId)).isZero();
    }

    @Test
    @DisplayName("★★ 連續呼叫會累加，不會每次都建新的一列")
    void tryConsume_shouldAccumulate() {

        /*
         * 這一題在保護 UPSERT 的 DO UPDATE 那一半。
         *
         * 若寫成單純的 INSERT，第二次就會撞主鍵爆掉。
         * 若 DO UPDATE 寫錯成 SET call_count = 1，計數永遠停在 1，
         * 配額等於沒有作用。
         */
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

        // 第 4 次要被擋下來
        assertThat(quotaService.tryConsume(userId)).isFalse();

        /*
         * 被拒絕時不可以增加計數。
         *
         * 若拒絕也照樣 +1，計數會一直往上爬。單看數字的話沒差，
         * 但「今天實際呼叫了幾次」這個資訊就失真了——
         * 而那正是這張表存在的目的。
         */
        assertThat(llmUsageRepository.countToday(userId)).isEqualTo(TEST_LIMIT);
    }

    @Test
    @DisplayName("★ 不同使用者的額度互不影響")
    void tryConsume_differentUsers_shouldBeIndependent() {

        /*
         * 主鍵是 (user_id, usage_date)。
         * 若少了 user_id，全部使用者會共用一個計數器——
         * 一個人用完，所有人都不能用。
         */
        Long otherId = userRepository.save(
                new User("quota-other-" + System.nanoTime() + "@example.com", "hash")).getId();

        for (int i = 0; i < TEST_LIMIT; i++) {
            quotaService.tryConsume(userId);
        }

        assertThat(quotaService.tryConsume(userId)).isFalse();
        assertThat(quotaService.tryConsume(otherId)).isTrue();
    }
}
