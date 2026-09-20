package dev.sift.source;

import dev.sift.support.PostgresTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sift.fetch.FailureType;
import dev.sift.fetch.FeedNotFoundException;
import dev.sift.fetch.FetchClient;
import dev.sift.fetch.FeedResolver;
import dev.sift.fetch.FetchJob;
import dev.sift.fetch.FetchJobRepository;
import dev.sift.fetch.FetchedArticle;
import dev.sift.fetch.FetchedItem;
import dev.sift.fetch.FetchedItemRepository;
import dev.sift.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SourceFlowIntegrationTest extends PostgresTestBase {
    private static final String OWNER_EMAIL = "source-owner@example.com";
    private static final String OTHER_EMAIL = "source-other@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeedResolver feedResolver;

    @MockitoBean
    private FetchClient fetchClient;

    private static final String EMPTY_RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>t</title><link>https://e.com</link>
            <description>d</description></channel></rss>
            """;

    @Autowired
    private FetchedItemRepository fetchedItemRepository;

    @Autowired
    private FetchJobRepository fetchJobRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private UserRepository userRepository;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        when(feedResolver.resolve(anyString())).thenAnswer(call -> call.getArgument(0));

        when(fetchClient.fetch(anyString())).thenReturn(EMPTY_RSS);

        ownerToken = registerAndLogin(OWNER_EMAIL);
        otherToken = registerAndLogin(OTHER_EMAIL);
    }

    @Test
    @DisplayName("1. 新增來源 → 201，enabled 預設為 true")
    void create_shouldReturnCreatedAndEnabled() throws Exception {
        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hacker News","url":"https://news.ycombinator.com/rss","type":"RSS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hacker News"))
                .andExpect(jsonPath("$.type").value("RSS"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("2. ★ 非 http/https 的網址 → 400（擋 SSRF）")
    void create_withNonHttpUrl_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"壞東西","url":"file:///etc/passwd","type":"RSS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"));
    }

    @Test
    @DisplayName("3. 列表只看得到自己的來源")
    void list_shouldOnlyReturnOwnSources() throws Exception {
        createSource(ownerToken, "我的來源", "https://mine.example.com/rss");
        createSource(otherToken, "別人的來源", "https://theirs.example.com/rss");

        assertThat(sourceCount(ownerToken)).isEqualTo(1);
        assertThat(sourceCount(otherToken)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$[0].name").value("我的來源"));
    }

    @Test
    @DisplayName("4. 改別人的來源 → 404，且對方的資料不變")
    void update_otherUsersSource_shouldReturnNotFound() throws Exception {
        long id = createSource(ownerToken, "原始名稱", "https://mine.example.com/rss");

        mockMvc.perform(patch("/api/v1/sources/" + id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"被竄改"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$[0].name").value("原始名稱"));
    }

    @Test
    @DisplayName("5. 刪除後列表看不到")
    void delete_shouldRemoveFromList() throws Exception {
        long id = createSource(ownerToken, "要刪掉的", "https://mine.example.com/rss");

        assertThat(sourceCount(ownerToken)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/sources/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(sourceCount(ownerToken)).isZero();
    }

    @Test
    @DisplayName("6. 刪別人的來源 → 404，且對方的來源還在")
    void delete_otherUsersSource_shouldReturnNotFound() throws Exception {
        long id = createSource(ownerToken, "動不了的", "https://mine.example.com/rss");

        mockMvc.perform(delete("/api/v1/sources/" + id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertThat(sourceCount(ownerToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("7. ★ PATCH 只給 name 時，enabled 必須維持原樣")
    void patch_withOnlyName_shouldNotChangeEnabled() throws Exception {
        long id = createSource(ownerToken, "原始名稱", "https://mine.example.com/rss");

        patchSource(id, """
                {"enabled":false}
                """).andExpect(jsonPath("$.enabled").value(false));

        patchSource(id, """
                {"name":"新名稱"}
                """)
                .andExpect(jsonPath("$.name").value("新名稱"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("8. 未登入存取來源 API → 401")
    void anyEndpoint_withoutToken_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/sources")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/sources")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/sources/1")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("9. ★ 同一個使用者重複訂閱同一個 url → 409")
    void create_withDuplicateUrl_shouldReturnConflict() throws Exception {
        String url = "https://dup.example.com/rss";

        createSource(ownerToken, "第一次", url);

        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"第二次","url":"%s","type":"RSS"}
                                """.formatted(url)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("來源已訂閱"));

        assertThat(sourceCount(ownerToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("10. ★ 不同使用者訂閱同一個 url → 兩邊都成功")
    void create_sameUrlByDifferentUsers_shouldBothSucceed() throws Exception {
        String url = "https://shared.example.com/rss";

        createSource(ownerToken, "我的", url);
        createSource(otherToken, "別人的", url);

        assertThat(sourceCount(ownerToken)).isEqualTo(1);
        assertThat(sourceCount(otherToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("11. ★ 刪除之後可以重新訂閱同一個 url")
    void create_afterDelete_shouldSucceed() throws Exception {
        String url = "https://readd.example.com/rss";

        long id = createSource(ownerToken, "第一次訂閱", url);

        mockMvc.perform(delete("/api/v1/sources/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        createSource(ownerToken, "重新訂閱", url);

        assertThat(sourceCount(ownerToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("12. ★ 填首頁網址 → 存進去的是 autodiscovery 找到的 feed 網址")
    void create_withHomepageUrl_shouldStoreDiscoveredFeedUrl() throws Exception {
        when(feedResolver.resolve("https://blog.example.com"))
                .thenReturn("https://blog.example.com/feed.xml");

        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"某部落格","url":"https://blog.example.com","type":"RSS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://blog.example.com/feed.xml"));
    }

    @Test
    @DisplayName("13. ★ 網址沒有提供 feed → 400，而不是存進去等明天才失敗")
    void create_withoutFeed_shouldReturnBadRequest() throws Exception {
        String url = "https://no-feed.example.com";

        when(feedResolver.resolve(url)).thenThrow(new FeedNotFoundException(url));

        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"沒有feed","url":"%s","type":"RSS"}
                                """.formatted(url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("找不到 feed"));

        assertThat(sourceCount(ownerToken)).isZero();
    }

    @Test
    @DisplayName("14. ★ 看別人的來源的抓取紀錄 → 404")
    void fetchJobs_otherUsersSource_shouldReturnNotFound() throws Exception {
        long id = createSource(ownerToken, "我的來源", "https://mine.example.com/rss");

        mockMvc.perform(get("/api/v1/sources/" + id + "/fetch-jobs")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("15. ★ 剛建立的來源已經抓過一輪了（Day 23 行為改變）")
    void fetchJobs_newSource_shouldHaveOneAutomaticJob() throws Exception {
        long id = createSource(ownerToken, "全新的來源", "https://brand-new.example.com/rss");

        mockMvc.perform(get("/api/v1/sources/" + id + "/fetch-jobs")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("15b. 停用中的來源沒有抓取紀錄 → 空陣列，不是 404")
    void fetchJobs_withoutAnyJob_shouldReturnEmptyList() throws Exception {
        long id = sourceRepository.saveAndFlush(new Source(
                userIdOf(OWNER_EMAIL), "沒抓過的來源",
                "https://never-fetched.example.com/rss", SourceType.RSS)).getId();

        mockMvc.perform(get("/api/v1/sources/" + id + "/fetch-jobs")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("16. ★★ 列出文章：新的在前，而且不含 rawContent")
    void items_shouldReturnArticlesNewestFirst() throws Exception {
        long sourceId = createSource(ownerToken, "有文章的來源", "https://has-items.example.com/rss");
        long jobId = fetchJobRepository.save(new FetchJob(sourceId)).getId();

        saveItem(sourceId, jobId, "第一篇", "hash-a");
        saveItem(sourceId, jobId, "第二篇", "hash-b");

        mockMvc.perform(get("/api/v1/sources/" + sourceId + "/items")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))

                .andExpect(jsonPath("$[0].title").value("第二篇"))

                .andExpect(jsonPath("$[0].rawContent").doesNotExist())

                .andExpect(jsonPath("$[0].sourceId").doesNotExist());
    }

    @Test
    @DisplayName("17. ★ 看別人的來源的文章 → 404")
    void items_otherUsersSource_shouldReturnNotFound() throws Exception {
        long id = createSource(ownerToken, "我的來源", "https://mine.example.com/rss");

        mockMvc.perform(get("/api/v1/sources/" + id + "/items")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("18. 剛建立的來源還沒抓過 → 空陣列，不是 404")
    void items_newSource_shouldReturnEmptyList() throws Exception {
        long id = createSource(ownerToken, "全新的來源", "https://brand-new.example.com/rss");

        mockMvc.perform(get("/api/v1/sources/" + id + "/items")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("19. ★ 摘要失敗的文章仍然會出現在列表裡")
    void items_failedItem_shouldStillBeListed() throws Exception {
        long sourceId = createSource(ownerToken, "有失敗的來源", "https://failed.example.com/rss");
        long jobId = fetchJobRepository.save(new FetchJob(sourceId)).getId();

        FetchedItem item = saveItem(sourceId, jobId, "摘要失敗的文章", "hash-failed");
        item.startSummarizing();
        item.failSummarization(FailureType.PERMANENT, "沒有可用的 API key");
        fetchedItemRepository.saveAndFlush(item);

        mockMvc.perform(get("/api/v1/sources/" + sourceId + "/items")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].summary").doesNotExist())
                .andExpect(jsonPath("$[0].failureReason").value("沒有可用的 API key"));
    }

    private FetchedItem saveItem(long sourceId, long jobId, String title, String hash) {
        FetchedItem item = new FetchedItem(sourceId, jobId, hash,
                new FetchedArticle(title, "https://example.com/" + hash, "內文", Instant.now()));

        return fetchedItemRepository.saveAndFlush(item);
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

    private long createSource(String token, String name, String url) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","url":"%s","type":"RSS"}
                                """.formatted(name, url)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions patchSource(long id, String json)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/sources/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private int sourceCount(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.size();
    }
}
