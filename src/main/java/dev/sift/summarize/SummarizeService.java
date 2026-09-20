package dev.sift.summarize;

import dev.sift.fetch.FailureType;
import dev.sift.fetch.FetchedItem;
import dev.sift.fetch.FetchedItemRepository;
import dev.sift.fetch.FetchedItemService;
import dev.sift.fetch.FetchedItemStatus;
import dev.sift.source.Source;
import dev.sift.source.SourceRepository;
import dev.sift.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Summarization pipeline: quota check, provider lookup, call, retry scheduling.
 */
@Service
public class SummarizeService {
    private static final Logger log = LoggerFactory.getLogger(SummarizeService.class);

    private final FetchedItemRepository fetchedItemRepository;
    private final FetchedItemService fetchedItemService;
    private final SourceRepository sourceRepository;
    private final UserService userService;
    private final SummarizerRegistry summarizerRegistry;
    private final RetryPolicy retryPolicy;
    private final QuotaService quotaService;

    private final int batchSize;

    public SummarizeService(FetchedItemRepository fetchedItemRepository,
                            FetchedItemService fetchedItemService,
                            SourceRepository sourceRepository,
                            UserService userService,
                            SummarizerRegistry summarizerRegistry,
                            RetryPolicy retryPolicy,
                            QuotaService quotaService,
                            @Value("${sift.summarize.batch-size}") int batchSize) {
        this.fetchedItemRepository = fetchedItemRepository;
        this.fetchedItemService = fetchedItemService;
        this.sourceRepository = sourceRepository;
        this.userService = userService;
        this.summarizerRegistry = summarizerRegistry;
        this.retryPolicy = retryPolicy;
        this.quotaService = quotaService;
        this.batchSize = batchSize;
    }

    public void summarizePending() {
        List<FetchedItem> pending = fetchedItemRepository
                .findProcessable(FetchedItemStatus.NEW.name(), batchSize);

        if (pending.isEmpty()) {
            return;
        }

        log.info("=== 摘要開始，待處理 {} 篇 ===", pending.size());

        int done = 0;
        int skipped = 0;

        for (FetchedItem item : pending) {
            try {
                if (summarizeOne(item)) {
                    done++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("處理文章時發生預期外的錯誤 itemId={}", item.getId(), e);
            }
        }

        log.info("=== 摘要結束，完成 {} 篇，跳過 {} 篇 ===", done, skipped);
    }

    // NOTE: the LLM call happens outside any transaction. Holding a pooled
    // connection across a multi-second network call starves the whole pool.
    private boolean summarizeOne(FetchedItem item) {
        Optional<Long> userId = ownerOf(item);
        if (userId.isEmpty()) {
            return false;
        }

        UserService.LlmCredentials credentials = userService.findLlmCredentials(userId.get());

        if (credentials == null) {
            log.debug("使用者剛移除 API key，跳過 itemId={} userId={}", item.getId(), userId.get());
            return false;
        }

        if (!quotaService.tryConsume(userId.get())) {
            return false;
        }

        Summarizer summarizer = summarizerRegistry.forProvider(credentials.provider());

        fetchedItemService.markSummarizing(item.getId());

        try {
            String summary = summarizer.summarize(
                    item.getTitle(), item.getRawContent(), credentials.apiKey());

            fetchedItemService.markSummarized(item.getId(), summary);
            return true;
        } catch (SummarizationException e) {
            handleFailure(item, e);
            return false;
        }
    }

    private void handleFailure(FetchedItem item, SummarizationException e) {
        boolean worthRetrying = e.getFailureType() == FailureType.TRANSIENT
                && retryPolicy.shouldRetry(item.getRetryCount());

        if (worthRetrying) {
            fetchedItemService.markForRetry(
                    item.getId(), e.getMessage(), retryPolicy.nextRetryAt(item.getRetryCount()));
            return;
        }

        String reason = e.getFailureType() == FailureType.TRANSIENT
                ? "重試 %d 次後仍然失敗：%s".formatted(retryPolicy.getMaxAttempts(), e.getMessage())
                : e.getMessage();

        fetchedItemService.markSummarizationFailed(item.getId(), e.getFailureType(), reason);
    }

    private Optional<Long> ownerOf(FetchedItem item) {
        Optional<Source> source = sourceRepository.findByIdAndDeletedAtIsNull(item.getSourceId());

        if (source.isEmpty()) {
            log.debug("來源已刪除，跳過 itemId={} sourceId={}", item.getId(), item.getSourceId());
            return Optional.empty();
        }

        return Optional.of(source.get().getUserId());
    }
}
