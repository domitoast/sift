package dev.sift.fetch;

import dev.sift.source.Source;
import dev.sift.source.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 把三個零件串起來：工作卡（FetchJob）、跑腿的（FetchClient）、翻譯的（FeedParser）。
 *
 * <h2>這個類別上刻意沒有 {@code @Transactional}</h2>
 *
 * 中間夾著一個最多 10 秒的 HTTP 請求。若整個流程包在一個 transaction 裡，
 * 那條資料庫連線就會被佔住 10 秒。
 *
 * <p>連線池只有 10 條（{@code maximum-pool-size: 10}）。
 * 10 個來源同時抓，連線池就空了——<b>此時所有使用者的 API 都會卡住</b>，
 * 只因為某個沒人在用的部落格回應很慢。
 *
 * <p>因此拆成：短 transaction（建任務）→ <b>沒有 transaction</b>（連網路）
 * → 短 transaction（記結果）。
 *
 * <p><b>原則：transaction 裡面不要做網路 I/O。</b>
 */
@Service
public class FetchService {

    private static final Logger log = LoggerFactory.getLogger(FetchService.class);

    private final SourceRepository sourceRepository;
    private final FetchJobService fetchJobService;
    private final FetchedItemService fetchedItemService;
    private final FetchClient fetchClient;
    private final FeedParser feedParser;

    public FetchService(SourceRepository sourceRepository,
                        FetchJobService fetchJobService,
                        FetchedItemService fetchedItemService,
                        FetchClient fetchClient,
                        FeedParser feedParser) {
        this.sourceRepository = sourceRepository;
        this.fetchJobService = fetchJobService;
        this.fetchedItemService = fetchedItemService;
        this.fetchClient = fetchClient;
        this.feedParser = feedParser;
    }

    /** 抓取所有啟用中的來源。 */
    public void fetchAll() {

        List<Source> sources = sourceRepository.findAllByEnabledTrueAndDeletedAtIsNull();

        log.info("=== 抓取開始，來源數={} ===", sources.size());

        for (Source source : sources) {
            try {
                fetchOne(source);

            } catch (Exception e) {
                /*
                 * 這個 catch 是整段迴圈最重要的一行。
                 *
                 * 沒有它，第一個來源丟出任何預期外的例外，
                 * 整輪抓取就結束了——後面九個來源今天都不會被抓，
                 * 而且沒有人會知道為什麼。
                 *
                 * 原則：批次處理中，一筆的失敗不可以影響其他筆。
                 */
                log.error("處理來源時發生預期外的錯誤 sourceId={}", source.getId(), e);
            }
        }

        log.info("=== 抓取結束 ===");
    }

    private void fetchOne(Source source) {

        // ① 短 transaction：建立任務並標記 RUNNING
        Long jobId = fetchJobService.startJob(source.getId());
        if (jobId == null) {
            return;   // 已有進行中的任務
        }

        // ② 沒有 transaction：這一段可能花上 10 秒
        try {
            String rawFeed = fetchClient.fetch(source.getUrl());
            List<FetchedArticle> articles = feedParser.parse(rawFeed);

            // ③ 一篇一個獨立的 transaction，重複的跳過
            int newCount = save(source, jobId, articles);

            log.info("來源「{}」取得 {} 篇，其中 {} 篇是新的",
                    source.getName(), articles.size(), newCount);

            // ④ 短 transaction：記錄結果
            fetchJobService.succeed(jobId);

        } catch (FeedFetchException e) {
            // 抓不到。分類由 FetchClient 決定，它才知道是 timeout 還是 404
            fetchJobService.fail(jobId, e.getFailureType(), e.getMessage());

        } catch (FeedParseException e) {
            // 抓到了但不是 feed。明天再抓還是同一份壞東西 → PERMANENT
            fetchJobService.fail(jobId, FailureType.PERMANENT, e.getMessage());
        }
    }

    /**
     * 一篇一篇存，回傳「新增了幾篇」。
     *
     * <p><b>迴圈裡每一次呼叫都是一個獨立的 transaction</b>——
     * {@code FetchedItemService.saveIfNew} 上有 {@code @Transactional}，
     * 而這個類別沒有。
     *
     * <p>不能包成一個大 transaction：PostgreSQL 在交易中一旦撞到唯一約束，
     * 整個交易就進入 aborted 狀態，後面的 INSERT 全部被拒絕。
     * 而「撞到重複」在這裡是每小時都會發生 25 次的正常情況。
     */
    private int save(Source source, Long jobId, List<FetchedArticle> articles) {

        int newCount = 0;

        for (FetchedArticle article : articles) {
            if (fetchedItemService.saveIfNew(source.getId(), jobId, article)) {
                newCount++;
                log.info("    + {}", article.title());
            }
        }

        return newCount;
    }
}
