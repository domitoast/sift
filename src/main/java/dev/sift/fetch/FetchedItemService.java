package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Stores fetched articles and drives their state transitions.
 *
 * saveIfNew is not transactional on purpose: it catches a unique-constraint
 * violation and returns normally, which inside a transaction would leave that
 * transaction marked rollback-only.
 */
@Service
public class FetchedItemService {
    private static final Logger log = LoggerFactory.getLogger(FetchedItemService.class);

    private final FetchedItemRepository fetchedItemRepository;
    private final ContentHasher contentHasher;

    public FetchedItemService(FetchedItemRepository fetchedItemRepository,
                              ContentHasher contentHasher) {
        this.fetchedItemRepository = fetchedItemRepository;
        this.contentHasher = contentHasher;
    }

    public boolean saveIfNew(Long sourceId, Long fetchJobId, FetchedArticle article) {
        String hash = contentHasher.hash(article.title(), article.content());

        FetchedItem item = new FetchedItem(sourceId, fetchJobId, hash, article);

        try {
            fetchedItemRepository.saveAndFlush(item);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("已存在，跳過 sourceId={} hash={}", sourceId, hash);
            return false;
        }
    }

    @Transactional
    public void markSummarizing(Long itemId) {
        load(itemId).startSummarizing();
    }

    @Transactional
    public void markSummarized(Long itemId, String summary) {
        load(itemId).summarized(summary);

        log.info("摘要完成 itemId={}", itemId);
    }

    @Transactional
    public void markSummarizationFailed(Long itemId, FailureType failureType, String reason) {
        load(itemId).failSummarization(failureType, reason);

        log.warn("摘要失敗 itemId={} 類型={} 原因={}", itemId, failureType, reason);
    }

    @Transactional
    public void markForRetry(Long itemId, String reason, Instant nextRetryAt) {
        FetchedItem item = load(itemId);
        item.retryLater(reason, nextRetryAt);

        log.info("暫時性失敗，第 {} 次重試排在 {} itemId={} 原因={}",
                item.getRetryCount(), nextRetryAt, itemId, reason);
    }

    private FetchedItem load(Long itemId) {
        return fetchedItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("找不到文章 itemId=" + itemId));
    }
}
