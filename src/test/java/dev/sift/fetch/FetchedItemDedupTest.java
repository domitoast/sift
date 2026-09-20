package dev.sift.fetch;

import dev.sift.support.PostgresTestBase;
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

@ActiveProfiles("test")
@SpringBootTest
class FetchedItemDedupTest extends PostgresTestBase {
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
        FetchedArticle same = article("標題", "https://x.com/1", "內文");

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId, same)).isTrue();
        assertThat(fetchedItemService.saveIfNew(sourceId, jobId, same)).isFalse();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★★ 網址帶了追蹤參數，但內容一樣 → 仍視為同一篇（ADR-004 的核心）")
    void saveIfNew_sameContentDifferentUrl_shouldBeDuplicate() {
        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/post?id=5", "內文"))).isTrue();

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/post?id=5&utm_source=rss", "內文"))).isFalse();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 內文改了一個字 → 視為新文章（ADR-004 已知的代價）")
    void saveIfNew_editedContent_shouldBeNew() {
        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/1", "內文"))).isTrue();

        assertThat(fetchedItemService.saveIfNew(sourceId, jobId,
                article("標題", "https://x.com/1", "內文修正版"))).isTrue();

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("★ 不同來源的同一篇文章 → 各存一份（唯一約束是 source_id + hash）")
    void saveIfNew_sameArticleDifferentSource_shouldBothSave() {
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
        FetchedArticle first = article("第一篇", "https://x.com/1", "內文1");

        fetchedItemService.saveIfNew(sourceId, jobId, first);
        fetchedItemService.saveIfNew(sourceId, jobId, first);

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
