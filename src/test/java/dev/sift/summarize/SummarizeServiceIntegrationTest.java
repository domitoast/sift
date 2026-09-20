package dev.sift.summarize;

import dev.sift.fetch.FailureType;
import dev.sift.fetch.FetchJob;
import dev.sift.fetch.FetchJobRepository;
import dev.sift.fetch.FetchedArticle;
import dev.sift.fetch.FetchedItem;
import dev.sift.fetch.FetchedItemRepository;
import dev.sift.fetch.FetchedItemStatus;
import dev.sift.source.Source;
import dev.sift.source.SourceRepository;
import dev.sift.source.SourceType;
import dev.sift.support.PostgresTestBase;
import dev.sift.user.User;
import dev.sift.user.UserRepository;
import dev.sift.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class SummarizeServiceIntegrationTest extends PostgresTestBase {
    @Autowired
    private SummarizeService summarizeService;

    @Autowired
    private FetchedItemRepository fetchedItemRepository;

    @Autowired
    private FetchJobRepository fetchJobRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @MockitoBean
    private SummarizerRegistry summarizerRegistry;

    private Summarizer summarizer;

    private Long userId;
    private Long sourceId;
    private Long jobId;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(
                new User("summarize-" + System.nanoTime() + "@example.com", "hash"));
        userId = user.getId();

        userService.updateLlmApiKey(userId, LlmProvider.FAKE, "sk-test-key");

        Source source = sourceRepository.saveAndFlush(
                new Source(userId, "測試來源", "https://s.example.com/rss", SourceType.RSS));
        sourceId = source.getId();

        jobId = fetchJobRepository.saveAndFlush(new FetchJob(sourceId)).getId();

        summarizer = mock(Summarizer.class);
        when(summarizerRegistry.forProvider(any())).thenReturn(summarizer);

        when(summarizer.summarize(anyString(), any(), anyString())).thenReturn("這是摘要");
    }

    @Test
    @DisplayName("1. 有 API key 的文章：NEW → READY，摘要真的寫進去")
    void summarize_shouldProduceReadyItem() {
        FetchedItem item = newItem("第一篇", "hash-1");

        summarizeService.summarizePending();

        FetchedItem result = reload(item);

        assertThat(result.getStatus()).isEqualTo(FetchedItemStatus.READY);

        assertThat(result.getSummary()).isEqualTo("這是摘要");
    }

    @Test
    @DisplayName("2. 使用者沒設 API key：文章根本不會被撈出來，停在 NEW")
    void summarize_withoutApiKey_shouldSkip() {
        userService.clearLlmApiKey(userId);

        FetchedItem item = newItem("沒有 key", "hash-2");

        summarizeService.summarizePending();

        assertThat(reload(item).getStatus()).isEqualTo(FetchedItemStatus.NEW);

        verify(summarizer, never()).summarize(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("★★ 重試成功之後，上一次失敗的痕跡要清乾淨")
    void summarize_afterRetry_shouldClearFailureFields() {
        FetchedItem item = newItem("重試後成功", "hash-retry-clear");

        item.startSummarizing();
        item.retryLater("Gemini 限流（429），稍後重試", Instant.now().minusSeconds(1));
        fetchedItemRepository.saveAndFlush(item);

        assertThat(reload(item).getFailureReason()).isNotNull();

        summarizeService.summarizePending();

        FetchedItem result = reload(item);

        assertThat(result.getStatus()).isEqualTo(FetchedItemStatus.READY);
        assertThat(result.getSummary()).isEqualTo("這是摘要");

        assertThat(result.getFailureType()).isNull();
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("3. ★ 配額用完：停在 NEW，而且 LLM 一次都沒被呼叫（錢沒花）")
    void summarize_whenQuotaExhausted_shouldNotCallLlm() {
        for (int i = 0; i < 3; i++) {
            FetchedItem consumed = newItem("耗配額 " + i, "hash-quota-" + i);
            summarizeService.summarizePending();
            assertThat(reload(consumed).getStatus()).isEqualTo(FetchedItemStatus.READY);
        }

        FetchedItem blocked = newItem("超出配額", "hash-3");

        summarizeService.summarizePending();

        assertThat(reload(blocked).getStatus()).isEqualTo(FetchedItemStatus.NEW);

        verify(summarizer, times(3)).summarize(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("4. PERMANENT 失敗 → FAILED，不排重試")
    void summarize_permanentFailure_shouldFailImmediately() {
        when(summarizer.summarize(anyString(), any(), anyString()))
                .thenThrow(new SummarizationException(FailureType.PERMANENT, "API key 無效"));

        FetchedItem item = newItem("永久失敗", "hash-4");

        summarizeService.summarizePending();

        FetchedItem result = reload(item);

        assertThat(result.getStatus()).isEqualTo(FetchedItemStatus.FAILED);
        assertThat(result.getFailureType()).isEqualTo(FailureType.PERMANENT);

        assertThat(result.getNextRetryAt()).isNull();
        assertThat(result.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("5. TRANSIENT 失敗 → 回 NEW，retry_count +1，next_retry_at 在未來")
    void summarize_transientFailure_shouldScheduleRetry() {
        when(summarizer.summarize(anyString(), any(), anyString()))
                .thenThrow(new SummarizationException(FailureType.TRANSIENT, "429 太多請求"));

        FetchedItem item = newItem("暫時失敗", "hash-5");

        summarizeService.summarizePending();

        FetchedItem result = reload(item);

        assertThat(result.getStatus()).isEqualTo(FetchedItemStatus.NEW);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getFailureType()).isEqualTo(FailureType.TRANSIENT);

        assertThat(result.getNextRetryAt())
                .isAfter(Instant.now().plus(40, ChronoUnit.SECONDS))
                .isBefore(Instant.now().plus(80, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("6. TRANSIENT 但重試次數用完 → FAILED，訊息說得出是哪一種失敗")
    void summarize_transientFailure_afterMaxAttempts_shouldFail() {
        when(summarizer.summarize(anyString(), any(), anyString()))
                .thenThrow(new SummarizationException(FailureType.TRANSIENT, "429 太多請求"));

        FetchedItem item = newItem("試到放棄", "hash-6");

        for (int i = 0; i < 3; i++) {
            item.startSummarizing();
            item.retryLater("前一次失敗", Instant.now().minusSeconds(1));
        }
        fetchedItemRepository.saveAndFlush(item);

        summarizeService.summarizePending();

        FetchedItem result = reload(item);

        assertThat(result.getStatus()).isEqualTo(FetchedItemStatus.FAILED);

        assertThat(result.getFailureReason()).contains("重試 3 次後仍然失敗");
    }

    @Test
    @DisplayName("7. next_retry_at 還沒到的文章不會被撿走")
    void summarize_shouldRespectNextRetryAt() {
        FetchedItem item = newItem("還在等", "hash-7");

        item.startSummarizing();
        item.retryLater("剛失敗", Instant.now().plus(1, ChronoUnit.HOURS));
        fetchedItemRepository.saveAndFlush(item);

        summarizeService.summarizePending();

        verify(summarizer, never()).summarize(anyString(), any(), anyString());
        assertThat(reload(item).getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("8. 一輪最多處理 batch-size 篇，多出來的留到下一輪")
    void summarize_shouldRespectBatchSize() {
        for (int i = 0; i < 3; i++) {
            newItem("第 " + i + " 篇", "hash-batch-" + i);
        }

        summarizeService.summarizePending();

        verify(summarizer, times(2)).summarize(anyString(), any(), anyString());

        long stillNew = fetchedItemRepository
                .findByStatusOrderByCreatedAtAsc(FetchedItemStatus.NEW, Limit.of(50))
                .stream()
                .filter(i -> i.getSourceId().equals(sourceId))
                .count();

        assertThat(stillNew).isEqualTo(1);
    }

    @Test
    @DisplayName("9. 一篇丟出預期外的例外，其他篇照樣被處理")
    void summarize_oneUnexpectedError_shouldNotStopTheBatch() {
        FetchedItem a = newItem("第一篇", "hash-boom");
        FetchedItem b = newItem("第二篇", "hash-ok");

        when(summarizer.summarize(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("完全沒預料到的錯誤"))
                .thenReturn("這是摘要");

        summarizeService.summarizePending();

        verify(summarizer, times(2)).summarize(anyString(), any(), anyString());

        var statuses = java.util.stream.Stream.of(reload(a), reload(b))
                .map(FetchedItem::getStatus)
                .toList();

        assertThat(statuses)
                .containsExactlyInAnyOrder(
                        FetchedItemStatus.READY,
                        FetchedItemStatus.SUMMARIZING);
    }

    @Test
    @DisplayName("10. 來源被 soft delete 之後，它的文章不會再被處理")
    void summarize_deletedSource_shouldSkip() {
        FetchedItem item = newItem("來源沒了", "hash-10");

        Source source = sourceRepository.findById(sourceId).orElseThrow();
        source.markDeleted();
        sourceRepository.saveAndFlush(source);

        summarizeService.summarizePending();

        verify(summarizer, never()).summarize(anyString(), any(), anyString());
        assertThat(reload(item).getStatus()).isEqualTo(FetchedItemStatus.NEW);
    }

    private FetchedItem newItem(String title, String hash) {
        return fetchedItemRepository.saveAndFlush(
                new FetchedItem(sourceId, jobId, hash,
                        new FetchedArticle(title, "https://example.com/" + hash,
                                "內文", Instant.now())));
    }

    private FetchedItem reload(FetchedItem item) {
        return fetchedItemRepository.findById(item.getId()).orElseThrow();
    }
}
