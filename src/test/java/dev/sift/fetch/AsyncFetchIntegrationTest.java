package dev.sift.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sift.source.Source;
import dev.sift.source.SourceRepository;
import dev.sift.source.SourceType;
import dev.sift.support.PostgresTestBase;
import dev.sift.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AsyncFetchIntegrationTest extends PostgresTestBase {
    private static final String OWNER_EMAIL = "async-owner@example.com";
    private static final String OTHER_EMAIL = "async-other@example.com";
    private static final String PASSWORD = "password123";

    private static final String RSS_WITH_TWO_ITEMS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
              <title>測試來源</title><link>https://async.example.com</link>
              <description>d</description>
              <item>
                <title>第一篇</title>
                <link>https://async.example.com/1</link>
                <description>內容一</description>
              </item>
              <item>
                <title>第二篇</title>
                <link>https://async.example.com/2</link>
                <description>內容二</description>
              </item>
            </channel></rss>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FetchJobRepository fetchJobRepository;

    @Autowired
    private FetchedItemRepository fetchedItemRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private StuckJobReaper reaper;

    @Autowired
    private FetchService fetchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private FeedResolver feedResolver;

    @MockitoBean
    private FetchClient fetchClient;

    private String ownerToken;
    private String otherToken;
    private long ownerUserId;

    @BeforeEach
    void setUp() throws Exception {
        when(feedResolver.resolve(anyString())).thenAnswer(call -> call.getArgument(0));
        when(fetchClient.fetch(anyString())).thenReturn(RSS_WITH_TWO_ITEMS);

        ownerToken = registerAndLogin(OWNER_EMAIL);
        otherToken = registerAndLogin(OTHER_EMAIL);
        ownerUserId = userIdOf(OWNER_EMAIL);
    }

    @Test
    @DisplayName("1. ★ 立刻抓取 → 202 Accepted，回 jobId 與 Location")
    void fetchNow_shouldReturnAcceptedWithJobId() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");

        String body = mockMvc.perform(post("/api/v1/sources/" + sourceId + "/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andReturn().getResponse().getContentAsString();

        long jobId = objectMapper.readTree(body).get("jobId").asLong();

        assertThat(fetchJobRepository.findById(jobId)).isPresent();
    }

    @Test
    @DisplayName("2. Location 標頭指向輪詢端點")
    void fetchNow_shouldSetLocationToPollingEndpoint() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");

        mockMvc.perform(post("/api/v1/sources/" + sourceId + "/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        Matchers.startsWith("/api/v1/fetch-jobs/")));
    }

    @Test
    @DisplayName("3. ★ 輪詢端點回得出結果——含 newItemCount 與 finished")
    void pollJob_shouldReturnResult() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");
        long jobId = fetchNow(sourceId);

        mockMvc.perform(get("/api/v1/fetch-jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.newItemCount").value(2))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.finishedAt").exists());
    }

    @Test
    @DisplayName("4. ★ finished 一定要出現在 JSON 裡——record 的額外方法預設不會被序列化")
    void pollJob_shouldSerializeFinishedFlag() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");
        long jobId = fetchNow(sourceId);

        String body = mockMvc.perform(get("/api/v1/fetch-jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).has("finished")).isTrue();
    }

    @Test
    @DisplayName("5. 抓到的文章真的進了資料庫")
    void fetchNow_shouldPersistItems() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");
        fetchNow(sourceId);

        assertThat(fetchedItemRepository.countBySourceId(sourceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("6. ★ 新增訂閱回應帶 fetchJobId，而且那筆任務真的跑過了")
    void createSource_shouldTriggerFirstFetch() throws Exception {
        String body = mockMvc.perform(post("/api/v1/sources")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"自動抓","url":"https://async.example.com/auto","type":"RSS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fetchJobId").isNumber())
                .andReturn().getResponse().getContentAsString();

        long jobId = objectMapper.readTree(body).get("fetchJobId").asLong();

        mockMvc.perform(get("/api/v1/fetch-jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.newItemCount").value(2));
    }

    @Test
    @DisplayName("7. ★ 已經有進行中的任務 → 409，而且不會多排一筆")
    void fetchNow_whenAlreadyRunning_shouldReturnConflict() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");

        fetchJobRepository.saveAndFlush(new FetchJob(sourceId));

        long before = fetchJobRepository.count();

        mockMvc.perform(post("/api/v1/sources/" + sourceId + "/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        assertThat(fetchJobRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("8. ★ 查別人的任務 → 404（不是 403）")
    void pollJob_ofAnotherUser_shouldReturnNotFound() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");
        long jobId = fetchNow(sourceId);

        mockMvc.perform(get("/api/v1/fetch-jobs/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("9. 查不存在的任務 → 404（與「不是你的」無法區分）")
    void pollJob_notFound_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/fetch-jobs/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("10. 未登入 → 401")
    void pollJob_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/fetch-jobs/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. ★ 來源被刪掉之後執行的任務 → 標成失敗，不會永遠卡在 PENDING")
    void runJob_whenSourceDeleted_shouldFailTheJob() throws Exception {
        long sourceId = createSource("等一下要刪", "https://async.example.com/gone");

        Source source = sourceRepository.findById(sourceId).orElseThrow();
        source.markDeleted();
        sourceRepository.saveAndFlush(source);

        Long jobId = fetchJobRepository.saveAndFlush(new FetchJob(sourceId)).getId();

        fetchService.runJob(sourceId, jobId);

        FetchJob job = fetchJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(FetchStatus.FAILED);
        assertThat(job.getFailureType()).isEqualTo(FailureType.PERMANENT);
        assertThat(job.isFinished()).isTrue();
    }

    @Test
    @DisplayName("12. ★ 卡住太久的任務 → 標成 FAILED，那個來源才抓得動")
    void reap_shouldFailStuckJobs() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");

        Long stuckId = fetchJobRepository.saveAndFlush(new FetchJob(sourceId)).getId();
        backdateJob(stuckId);

        reaper.reap();
        em.flush();

        FetchJob job = fetchJobRepository.findById(stuckId).orElseThrow();

        assertThat(job.getStatus()).isEqualTo(FetchStatus.FAILED);

        assertThat(job.getFailureType()).isEqualTo(FailureType.TRANSIENT);

        mockMvc.perform(post("/api/v1/sources/" + sourceId + "/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("13. ★ 剛建立的任務不會被回收——回收條件是「太久」不是「還沒結束」")
    void reap_shouldNotTouchFreshJobs() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");

        Long freshId = fetchJobRepository.saveAndFlush(new FetchJob(sourceId)).getId();

        reaper.reap();
        em.flush();

        assertThat(fetchJobRepository.findById(freshId).orElseThrow().getStatus())
                .isEqualTo(FetchStatus.PENDING);
    }

    @Test
    @DisplayName("14. ★ 卡在 SUMMARIZING 的文章 → FAILED，使用者才看得到「重新摘要」")
    void reap_shouldFailStuckSummarizingItems() throws Exception {
        long sourceId = createSource("測試來源", "https://async.example.com/rss");
        Long jobId = fetchJobRepository.saveAndFlush(new FetchJob(sourceId)).getId();

        FetchedItem item = fetchedItemRepository.saveAndFlush(new FetchedItem(
                sourceId, jobId, "stuck-hash",
                new FetchedArticle("卡住的文章", "https://async.example.com/stuck", "內容", null)));

        item.startSummarizing();
        fetchedItemRepository.saveAndFlush(item);
        backdateItem(item.getId());

        reaper.reap();
        em.flush();

        FetchedItem reaped = fetchedItemRepository.findById(item.getId()).orElseThrow();

        assertThat(reaped.getStatus()).isEqualTo(FetchedItemStatus.FAILED);
        assertThat(reaped.getFailureType()).isEqualTo(FailureType.TRANSIENT);
        assertThat(reaped.getFailureReason()).contains("中斷");
    }

    private void backdateJob(Long jobId) {
        em.flush();
        em.createNativeQuery(
                        "UPDATE fetch_job SET created_at = now() - interval '1 hour' WHERE id = :id")
                .setParameter("id", jobId)
                .executeUpdate();
        em.clear();
    }

    private void backdateItem(Long itemId) {
        em.flush();

        em.createNativeQuery(
                "ALTER TABLE fetched_item DISABLE TRIGGER trg_fetched_item_updated_at")
                .executeUpdate();

        em.createNativeQuery(
                        "UPDATE fetched_item SET updated_at = now() - interval '1 hour' WHERE id = :id")
                .setParameter("id", itemId)
                .executeUpdate();

        em.createNativeQuery(
                "ALTER TABLE fetched_item ENABLE TRIGGER trg_fetched_item_updated_at")
                .executeUpdate();

        em.clear();
    }

    private long fetchNow(long sourceId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sources/" + sourceId + "/fetch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("jobId").asLong();
    }

    private long createSource(String name, String url) {
        Source source = sourceRepository.saveAndFlush(
                new Source(ownerUserId, name, url, SourceType.RSS));
        return source.getId();
    }

    private long userIdOf(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow().getId();
    }

    private String registerAndLogin(String email) throws Exception {
        String credentials = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }
}
