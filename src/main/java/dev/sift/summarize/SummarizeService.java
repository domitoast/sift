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
 * 把待處理的文章送去產生摘要。
 *
 * <p><b>這個類別刻意沒有 {@code @Transactional}</b>：中間夾著一個呼叫 LLM 的
 * 網路請求，可能要好幾秒。包在交易裡會佔住資料庫連線，
 * 理由與 {@code FetchService} 完全相同。
 *
 * <p>流程：
 *
 * <pre>
 * 撈出 status = NEW 的前 N 筆
 *    │
 *    └─ 每一筆：
 *         ① 找出這篇屬於誰 → 取出那個人的 API key
 *         ② 沒有 key → 跳過，停在 NEW（ADR-003：不視為失敗）
 *         ③ 短交易：標記 SUMMARIZING
 *         ④ 沒有交易：呼叫 LLM      ← 慢的部分
 *         ⑤ 短交易：標記 READY 或 FAILED
 * </pre>
 */
@Service
public class SummarizeService {

    private static final Logger log = LoggerFactory.getLogger(SummarizeService.class);

    private final FetchedItemRepository fetchedItemRepository;
    private final FetchedItemService fetchedItemService;
    private final SourceRepository sourceRepository;
    private final UserService userService;
    private final Summarizer summarizer;
    private final RetryPolicy retryPolicy;

    private final int batchSize;

    public SummarizeService(FetchedItemRepository fetchedItemRepository,
                            FetchedItemService fetchedItemService,
                            SourceRepository sourceRepository,
                            UserService userService,
                            Summarizer summarizer,
                            RetryPolicy retryPolicy,
                            @Value("${sift.summarize.batch-size}") int batchSize) {

        this.fetchedItemRepository = fetchedItemRepository;
        this.fetchedItemService = fetchedItemService;
        this.sourceRepository = sourceRepository;
        this.userService = userService;
        this.summarizer = summarizer;
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
    }

    /** 處理一批待摘要的文章。 */
    public void summarizePending() {

        /*
         * 只撈「擁有者已經設定 API key」的文章。
         *
         * Day 18 是撈最舊的 10 筆再逐一檢查有沒有 key——
         * 結果沒有 key 的那幾筆永遠佔著名額，後面的永遠輪不到
         * （head-of-line blocking）。
         *
         * 過濾放進查詢條件才是真的排除。
         */
        List<FetchedItem> pending = fetchedItemRepository
                .findProcessable(FetchedItemStatus.NEW.name(), batchSize);

        if (pending.isEmpty()) {
            return;   // 沒事做的時候不要留下 log，否則每分鐘一行噪音
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
                /*
                 * 同 FetchService：一篇的失敗不可以中斷整批。
                 * 沒有這個 catch，第一篇丟出預期外的例外，
                 * 後面九篇今天都不會被處理，而且沒有人知道為什麼。
                 */
                log.error("處理文章時發生預期外的錯誤 itemId={}", item.getId(), e);
            }
        }

        log.info("=== 摘要結束，完成 {} 篇，跳過 {} 篇 ===", done, skipped);
    }

    /** @return true 代表真的做了摘要；false 代表跳過（沒有 API key） */
    private boolean summarizeOne(FetchedItem item) {

        Optional<Long> userId = ownerOf(item);
        if (userId.isEmpty()) {
            return false;
        }

        /*
         * 明文的 API key 只在這個區域變數裡存在，用完就離開作用域。
         *
         * ⚠️ 絕對不要把它放進任何物件的欄位、不要記進日誌、
         *    不要放進例外訊息。ADR-003 的第三條。
         */
        String apiKey = userService.findDecryptedApiKey(userId.get());

        if (apiKey == null) {
            /*
             * 正常情況下走不到這裡——findProcessable 已經把沒有 key 的排除了。
             *
             * 但查詢和這一行之間有時間差：使用者可能剛好在這個瞬間
             * 呼叫 DELETE /me/llm-key 把 key 刪掉了。
             *
             * 這是 race condition 的又一個形狀。處置與 ADR-003 一致：
             * 停在 NEW，不視為失敗。他重新設定 key 之後，下一輪會自己處理。
             */
            log.debug("使用者剛移除 API key，跳過 itemId={} userId={}", item.getId(), userId.get());
            return false;
        }

        // ③ 短交易
        fetchedItemService.markSummarizing(item.getId());

        try {
            // ④ 沒有交易：這一段可能好幾秒
            String summary = summarizer.summarize(item.getTitle(), item.getRawContent(), apiKey);

            // ⑤ 短交易
            fetchedItemService.markSummarized(item.getId(), summary);
            return true;

        } catch (SummarizationException e) {
            handleFailure(item, e);
            return false;
        }
    }

    /**
     * 失敗之後決定：排隊重試，還是就此放棄。
     *
     * <pre>
     * PERMANENT              → FAILED（重試幾次都一樣）
     * TRANSIENT + 還有次數    → 回到 NEW，等 nextRetryAt 到了再撿
     * TRANSIENT + 次數用完    → FAILED
     * </pre>
     */
    private void handleFailure(FetchedItem item, SummarizationException e) {

        boolean worthRetrying = e.getFailureType() == FailureType.TRANSIENT
                && retryPolicy.shouldRetry(item.getRetryCount());

        if (worthRetrying) {
            fetchedItemService.markForRetry(
                    item.getId(), e.getMessage(), retryPolicy.nextRetryAt(item.getRetryCount()));
            return;
        }

        /*
         * 走到這裡有兩種情況，訊息要分得出來——
         * 「網址壞了」和「試了三次都不行」需要的處置不一樣。
         */
        String reason = e.getFailureType() == FailureType.TRANSIENT
                ? "重試 %d 次後仍然失敗：%s".formatted(retryPolicy.getMaxAttempts(), e.getMessage())
                : e.getMessage();

        fetchedItemService.markSummarizationFailed(item.getId(), e.getFailureType(), reason);
    }

    /**
     * 找出這篇文章屬於哪個使用者。
     *
     * <p>{@code fetched_item} 依 ADR-012 只存 {@code source_id}，沒有 {@code user_id}，
     * 所以要透過 source 繞一層。
     */
    private Optional<Long> ownerOf(FetchedItem item) {

        Optional<Source> source = sourceRepository.findByIdAndDeletedAtIsNull(item.getSourceId());

        if (source.isEmpty()) {
            // 來源被刪掉了，但文章還在（ON DELETE RESTRICT + soft delete 的結果）
            log.debug("來源已刪除，跳過 itemId={} sourceId={}", item.getId(), item.getSourceId());
            return Optional.empty();
        }

        return Optional.of(source.get().getUserId());
    }
}
