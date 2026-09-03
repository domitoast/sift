package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 把抓到的文章寫進暫存區，重複的跳過。
 *
 * <h2>insert-or-ignore：為什麼不「先查再寫」</h2>
 *
 * ADR-004 明確指定用 insert-or-ignore：直接 INSERT，撞到唯一約束就跳過。
 * 這與 {@code SourceService.create()} 的「先查再寫 + 第二道防線」相反，
 * 差別在於<b>有沒有人在等一個錯誤訊息</b>：
 *
 * <table border="1">
 *   <tr><th></th><th>新增訂閱來源</th><th>抓取存文章</th></tr>
 *   <tr><td>誰觸發</td><td>使用者按按鈕</td><td>排程，半夜三點</td></tr>
 *   <tr><td>重複時該做什麼</td><td>告訴他「已訂閱過」</td><td>跳過，繼續下一篇</td></tr>
 *   <tr><td>一次處理幾筆</td><td>1 筆</td><td>30 筆，其中 25 筆重複</td></tr>
 * </table>
 *
 * <p>「先查」存在的唯一理由是產生好的錯誤訊息。排程不需要——
 * 重複是<b>預期中的正常情況</b>，不是錯誤。
 * 而且先查的話，30 篇就要多 30 次查詢。
 *
 * <h2>⚠️ 為什麼這個方法「沒有」 {@code @Transactional}</h2>
 *
 * 這裡踩過一次坑，值得記下來。
 *
 * <p>原本的寫法是在這個方法上加 {@code @Transactional}，然後在裡面
 * catch 住 {@code DataIntegrityViolationException} 並回傳 false。
 * 結果四個測試同時爆掉：
 *
 * <pre>
 * UnexpectedRollback: Transaction silently rolled back because it has been marked as rollback-only
 * </pre>
 *
 * <p><b>原因</b>：交易中一旦發生資料庫錯誤，那個交易就被標記為
 * rollback-only，<b>catch 住不會讓它復活</b>。
 * 方法正常回傳後 Spring 想 commit，但那個交易已經不能 commit 了。
 *
 * <p><b>對照 {@code UserService.register()}</b>：它也是
 * {@code @Transactional} + catch，但它在 catch 裡<b>往外丟例外</b>——
 * 丟例外 → 回滾 → 一切符合預期。差別在這裡。
 *
 * <p><b>結論：catch 必須在交易外面。</b>
 * 這個方法不標 {@code @Transactional}，
 * {@code saveAndFlush} 會在自己的交易裡執行（Spring Data 的 repository
 * 方法本身就是交易性的），失敗時那個交易自己回滾，
 * 例外才乾淨地傳到這裡被接住。
 *
 * <p>順帶解決了另一個問題：每一篇各自一個交易，
 * 第 5 篇撞到重複不會害得後面 25 篇全部寫不進去。
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

    /**
     * 寫入一篇文章，已經存在就跳過。
     *
     * @return true 代表這是新的一篇；false 代表已經有了
     */
    public boolean saveIfNew(Long sourceId, Long fetchJobId, FetchedArticle article) {

        String hash = contentHasher.hash(article.title(), article.content());

        FetchedItem item = new FetchedItem(sourceId, fetchJobId, hash, article);

        try {
            /*
             * saveAndFlush 而不是 save：強迫 INSERT 立刻送出，
             * 唯一約束的衝突才會在這裡被接住，而不是延後到某個看不見的地方。
             *
             * 因為這個方法沒有 @Transactional，
             * saveAndFlush 會開一個自己的交易，失敗時那個交易自行回滾。
             */
            fetchedItemRepository.saveAndFlush(item);
            return true;

        } catch (DataIntegrityViolationException e) {
            /*
             * 撞到 uq_fetched_item_source_hash。
             *
             * 這不是錯誤，是這個設計預期中的正常路徑——
             * RSS 每次都回最近 30 篇，其中大部分本來就抓過了。
             *
             * 因此用 debug 等級，不是 warn。
             * 若用 warn，正常運作的系統每小時會產生 25 行警告，
             * 真正的問題就被淹沒了。
             */
            log.debug("已存在，跳過 sourceId={} hash={}", sourceId, hash);
            return false;
        }
    }

    /**
     * 標記為處理中：NEW → SUMMARIZING。
     *
     * <p>這三個方法都標 {@code @Transactional}，而且都只做一次狀態改變。
     * 它們刻意<b>不</b>包住呼叫 LLM 的那一段——那可能要好幾秒，
     * 包進交易就會佔住資料庫連線，理由與 {@code FetchService} 相同。
     */
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

    /**
     * 暫時性失敗，排隊等下次重試。
     *
     * @param nextRetryAt 由 {@code RetryPolicy} 算出的時間點
     */
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
