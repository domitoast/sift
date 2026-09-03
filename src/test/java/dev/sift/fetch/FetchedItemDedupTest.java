package dev.sift.fetch;

import dev.sift.source.Source;
import dev.sift.source.SourceRepository;
import dev.sift.source.SourceType;
import dev.sift.user.User;
import dev.sift.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dedup 與 idempotency 的驗證（NFR-2.2）。
 *
 * <p><b>為什麼是整合測試而不是 unit test</b>：
 * 這裡要驗證的是資料庫的唯一約束 {@code uq_fetched_item_source_hash}
 * 有沒有真的擋住。把 repository mock 掉的話，測到的會是
 * 「我叫 mock 回 false，它就回了 false」——什麼都沒證明。
 *
 * <p>⚠️ 這個類別沒有 {@code @Transactional} 在 class 上，
 * 因為 {@code FetchedItemService.saveIfNew} 需要自己的交易邊界
 * （撞到唯一約束時，交易會進入 aborted 狀態）。
 * 因此測試自己負責清理。
 */
@ActiveProfiles("test")
@SpringBootTest
class FetchedItemDedupTest {

    @Autowired
    private FetchedItemService fetchedItemService;

    @Autowired
    private FetchedItemRepository fetchedItemRepository;

    @Autowired
    private FetchJobRepository fetchJobRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private UserRepository userRepository;

    private Long sourceId;
    private Long otherSourceId;
    private Long jobId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                new User("dedup-" + System.nanoTime() + "@example.com", "hash"));

        sourceId = sourceRepository.save(
                new Source(user.getId(), "來源A", "https://a.example.com/rss", SourceType.RSS))
                .getId();

        otherSourceId = sourceRepository.save(
                new Source(user.getId(), "來源B", "https://b.example.com/rss", SourceType.RSS))
                .getId();

        FetchJob job = new FetchJob(sourceId);
        job.start();
        jobId = fetchJobRepository.save(job).getId();
    }

    private FetchedArticle article(String title, String link, String content) {
        return new FetchedArticle(title, link, content, Instant.now());
    }

    // ---------- 核心 ----------

    @Test
    @DisplayName("第一次存 → 新的")
    void saveIfNew_firstTime_shouldBeNew() {

        boolean saved = fetchedItemService.saveIfNew(
                sourceId, jobId, article("標題", "https://x.com/1", "內文"));

        assertThat(saved).isTrue();
        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★★ 同一篇存第二次 → 不是新的，而且資料庫不會多一筆（NFR-2.2）")
    void saveIfNew_sameArticleTwice_shouldNotDuplicate() {

        /*
         * 這就是 idempotency：同一個操作做一次和做兩次，結果相同。
         *
         * 沒有它的話，排程每小時抓一次，一天就會存出 24 份一樣的東西，
         * 而 Day 18 加上 LLM 摘要之後，那是 24 倍的費用。
         */
        FetchedArticle same = article("標題", "https://x.com/1", "內文");

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId, same)).isTrue();
        assertThat(fetchedItemService.saveIfNew(sourceId, jobId, same)).isFalse();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★★ 網址帶了追蹤參數，但內容一樣 → 仍視為同一篇（ADR-004 的核心）")
    void saveIfNew_sameContentDifferentUrl_shouldBeDuplicate() {

        /*
         * 這一題是「為什麼用內容雜湊而不是網址」的證據。
         *
         * 同一篇文章的連結每天可能長得不一樣：
         *   今天  example.com/post?id=5
         *   明天  example.com/post?id=5&utm_source=rss
         *
         * 若 dedup 依網址判斷，這兩者會被當成兩篇不同的文章，
         * 於是重複存、重複付費做摘要——而且每天都會發生一次。
         */
        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/post?id=5", "內文"))).isTrue();

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/post?id=5&utm_source=rss", "內文"))).isFalse();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 內文改了一個字 → 視為新文章（ADR-004 已知的代價）")
    void saveIfNew_editedContent_shouldBeNew() {

        /*
         * 這是採用內容雜湊必然的代價，ADR-004 明確接受了它：
         * 「網址雜訊每天發生，原文修錯字一年僅數次，優先解決高頻問題」。
         *
         * 這題不是在測「正確行為」，是在把這個代價記錄下來——
         * 哪天有人覺得這是 bug，會先看到這題和它的說明。
         */
        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/1", "內文"))).isTrue();

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/1", "內文修正版"))).isTrue();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("★ 不同來源的同一篇文章 → 各存一份（唯一約束是 source_id + hash）")
    void saveIfNew_sameArticleDifferentSource_shouldBothSave() {

        /*
         * 唯一約束是 (source_id, content_hash) 而不是 content_hash。
         *
         * 兩個新聞網站轉載同一篇稿子，使用者兩邊都訂閱了——
         * 他會想知道兩邊都出現了這則新聞。
         */
        FetchedArticle same = article("同一篇稿子", "https://x.com/1", "內文");

        FetchJob otherJob = new FetchJob(otherSourceId);
        otherJob.start();
        Long otherJobId = fetchJobRepository.save(otherJob).getId();

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId, same)).isTrue();
        assertThat(fetchedItemService.saveIfNew(otherSourceId, otherJobId, same)).isTrue();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
        assertThat(fetchedItemRepository.countBySourceId(otherSourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 撞到重複之後，後面的文章仍然存得進去")
    void saveIfNew_afterDuplicate_shouldStillSaveOthers() {

        /*
         * 這一題保護的是「每篇一個獨立 transaction」的設計。
         *
         * 若把 30 篇包在同一個交易裡，第 2 篇撞到重複時 PostgreSQL 會把
         * 整個交易標記為 aborted，後面的 INSERT 全部被拒絕：
         *
         *   current transaction is aborted, commands ignored until end of transaction block
         *
         * 而「撞到重複」是每小時發生 25 次的正常情況。
         */
        FetchedArticle first = article("第一篇", "https://x.com/1", "內文1");

        fetchedItemService.saveIfNew(sourceId, jobId, first);
        fetchedItemService.saveIfNew(sourceId, jobId, first);          // 重複，被跳過

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("第三篇", "https://x.com/3", "內文3"))).isTrue();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("★ 沒有內文的文章也能存（很多 feed 只給標題和連結）")
    void saveIfNew_nullContent_shouldWork() {

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("只有標題", "https://x.com/1", null))).isTrue();

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("只有標題", "https://x.com/1", null))).isFalse();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
    }
}
