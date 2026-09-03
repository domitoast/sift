package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FetchedItem 狀態機的 unit test。不碰資料庫。
 *
 * <pre>
 * NEW ──startSummarizing()──> SUMMARIZING ──summarized()──────> READY
 *                                   └──failSummarization()──> FAILED
 * </pre>
 */
class FetchedItemStateTest {

    private FetchedItem newItem() {
        return new FetchedItem(1L, 1L, "hash",
                new FetchedArticle("標題", "https://x.com/1", "內文", Instant.now()));
    }

    // ---------- 合法的路徑 ----------

    @Test
    @DisplayName("剛建立是 NEW，沒有摘要")
    void newItem_shouldBeNew() {

        FetchedItem item = newItem();

        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.NEW);
        assertThat(item.getSummary()).isNull();
    }

    @Test
    @DisplayName("NEW → SUMMARIZING → READY，摘要跟著寫進去")
    void happyPath_shouldReachReady() {

        FetchedItem item = newItem();

        item.startSummarizing();
        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.SUMMARIZING);

        item.summarized("這是摘要");

        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.READY);
        assertThat(item.getSummary()).isEqualTo("這是摘要");
    }

    @Test
    @DisplayName("SUMMARIZING → FAILED，原因跟著記下來")
    void failSummarization_shouldRecordReason() {

        FetchedItem item = newItem();
        item.startSummarizing();

        item.failSummarization(FailureType.TRANSIENT, "429 rate limit");

        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.FAILED);
        assertThat(item.getFailureType()).isEqualTo(FailureType.TRANSIENT);
        assertThat(item.getFailureReason()).isEqualTo("429 rate limit");
    }

    // ---------- 不合法的路徑 ----------

    @Test
    @DisplayName("★★ READY 不能沒有摘要——空字串也不行")
    void summarized_blank_shouldThrow() {

        /*
         * 資料庫的 ck_fetched_item_summary 也擋這件事：
         *   CHECK (status NOT IN ('READY','PROMOTED') OR summary IS NOT NULL)
         *
         * 兩層擋同一件事。這一層的好處是「當場就爆」，
         * 而不是等到 flush 到資料庫時才收到看不懂的約束違反訊息。
         */
        FetchedItem item = newItem();
        item.startSummarizing();

        assertThatThrownBy(() -> item.summarized("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> item.summarized(null))
                .isInstanceOf(IllegalArgumentException.class);

        // 失敗之後狀態不可以被改掉
        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.SUMMARIZING);
    }

    @Test
    @DisplayName("★ NEW 不能直接跳到 READY")
    void summarized_fromNew_shouldThrow() {

        assertThatThrownBy(() -> newItem().summarized("摘要"))
                .isInstanceOf(IllegalFetchedItemTransitionException.class);
    }

    @Test
    @DisplayName("★ 已經 READY 的不能再摘要一次——那等於重複付費")
    void startSummarizing_fromReady_shouldThrow() {

        FetchedItem item = newItem();
        item.startSummarizing();
        item.summarized("摘要");

        assertThatThrownBy(item::startSummarizing)
                .isInstanceOf(IllegalFetchedItemTransitionException.class);
    }

    @Test
    @DisplayName("★ 已經在 SUMMARIZING 的不能再被撿走一次")
    void startSummarizing_twice_shouldThrow() {

        /*
         * 防的是「兩個工作者同時撿到同一篇」——
         * 那會呼叫兩次 LLM，付兩次錢，拿到兩份摘要。
         */
        FetchedItem item = newItem();
        item.startSummarizing();

        assertThatThrownBy(item::startSummarizing)
                .isInstanceOf(IllegalFetchedItemTransitionException.class);
    }

    @Test
    @DisplayName("★★ 暫時性失敗 → 回到 NEW 排隊，retryCount 加一")
    void retryLater_shouldGoBackToNew() {

        /*
         * 為什麼是回到 NEW 而不是 FAILED：
         *
         * FAILED 的語意是「這件事結束了」。
         * 但暫時性失敗還沒結束——它只是還沒成功。
         *
         * 回到 NEW 就是「重新排隊」，而 nextRetryAt 讓它不會馬上被撿走。
         */
        FetchedItem item = newItem();
        item.startSummarizing();

        Instant later = Instant.now().plusSeconds(60);
        item.retryLater("429 rate limit", later);

        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.NEW);
        assertThat(item.getRetryCount()).isEqualTo(1);
        assertThat(item.getNextRetryAt()).isEqualTo(later);

        // 失敗原因要留著——排隊中的項目也該看得出上次為什麼失敗
        assertThat(item.getFailureType()).isEqualTo(FailureType.TRANSIENT);
        assertThat(item.getFailureReason()).isEqualTo("429 rate limit");
    }

    @Test
    @DisplayName("★ 重試多次，retryCount 會累加")
    void retryLater_multipleTimes_shouldAccumulate() {

        FetchedItem item = newItem();

        for (int i = 1; i <= 3; i++) {
            item.startSummarizing();
            item.retryLater("timeout", Instant.now().plusSeconds(60));

            assertThat(item.getRetryCount()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("★ 沒在 SUMMARIZING 就不能排重試")
    void retryLater_fromNew_shouldThrow() {

        assertThatThrownBy(() -> newItem().retryLater("x", Instant.now()))
                .isInstanceOf(IllegalFetchedItemTransitionException.class);
    }

    @Test
    @DisplayName("★ 失敗之後不能改口說成功")
    void summarized_fromFailed_shouldThrow() {

        FetchedItem item = newItem();
        item.startSummarizing();
        item.failSummarization(FailureType.PERMANENT, "內容為空");

        assertThatThrownBy(() -> item.summarized("摘要"))
                .isInstanceOf(IllegalFetchedItemTransitionException.class);

        assertThat(item.getStatus()).isEqualTo(FetchedItemStatus.FAILED);
        assertThat(item.getFailureReason()).isEqualTo("內容為空");
    }
}
