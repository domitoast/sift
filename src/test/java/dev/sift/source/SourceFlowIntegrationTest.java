package dev.sift.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sift.fetch.FeedNotFoundException;
import dev.sift.fetch.FeedResolver;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 訂閱來源的整合測試。
 *
 * <p>測試案例由使用者在實作後列出（3–6 題），第 7 題為 Claude 補充。
 *
 * <p>第 9–11 題為 Day 14 空手日的驗收：擋重複訂閱。
 * 三題分別對應 {@code existsByUrlAndUserIdAndDeletedAtIsNull} 這個方法名字裡的
 * 三段條件——{@code Url}、{@code UserId}、{@code DeletedAtIsNull}，
 * 少任何一段都會有一題變紅。
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SourceFlowIntegrationTest {

    private static final String OWNER_EMAIL = "source-owner@example.com";
    private static final String OTHER_EMAIL = "source-other@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 用假的 {@link FeedResolver} 取代真的那一個。
     *
     * <p><b>為什麼必須這樣做</b>：Day 16 起，新增來源會真的去抓一次網址。
     * 若不替換，這個檔案裡每一次 {@code POST /sources} 都會對外發出 HTTP 請求：
     *
     * <ul>
     *   <li>測試變慢——每題都要等網路</li>
     *   <li>測試變得不穩定——別人的網站掛掉，我們的 CI 就紅</li>
     *   <li>{@code mine.example.com} 這類假網址根本抓不到，所有測試都會失敗</li>
     * </ul>
     *
     * <p><b>整合測試不該依賴外部網路。</b>
     * 而 {@code FeedResolver} 自己的行為，由它自己的測試負責。
     *
     * <p>{@code @MockitoBean} 是 Spring Boot 3.4 起的寫法，
     * 取代舊的 {@code @MockBean}（已標記為 deprecated）。
     */
    @MockitoBean
    private FeedResolver feedResolver;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        // 預設行為：使用者填什麼就回什麼（假裝填的本來就是 feed 網址）
        when(feedResolver.resolve(anyString())).thenAnswer(call -> call.getArgument(0));

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

        /*
         * file:// 若不擋，等 Day 15 排程去「抓」的時候，
         * 就變成讀取伺服器本機的檔案。
         */
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

        // 光是回 404 不夠——要確認資料真的沒被改
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

        // 先停用
        patchSource(id, """
                {"enabled":false}
                """).andExpect(jsonPath("$.enabled").value(false));

        /*
         * 這一條在保護 UpdateSourceRequest 的 enabled 是 Boolean 而非 boolean。
         *
         * 若改成原始型別 boolean，JSON 沒帶這個欄位時 Jackson 會填入預設值 false——
         * 使用者只是改個名字，來源卻被靜靜停用，而且沒有任何錯誤。
         *
         * 這正是 PATCH 與 PUT 的核心差別：PATCH 必須分得出
         * 「沒給這個欄位」和「給了 false」。
         */
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

        /*
         * 這一條在保護 SourceService.create() 開頭的 existsBy 檢查。
         *
         * 把那個 if 註解掉，這個測試不會變綠——它會變成 409 以外的狀態碼
         * （資料庫的 uq_source_user_url 會擋下來，但沒人處理就是 500）。
         *
         * 換句話說：這個測試同時證明了「有擋」與「擋出來的是正確的狀態碼」。
         */
        mockMvc.perform(post("/api/v1/sources")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"第二次","url":"%s","type":"RSS"}
                                """.formatted(url)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("來源已訂閱"));

        // 光是回 409 不夠——要確認真的沒有多存一筆
        assertThat(sourceCount(ownerToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("10. ★ 不同使用者訂閱同一個 url → 兩邊都成功")
    void create_sameUrlByDifferentUsers_shouldBothSucceed() throws Exception {

        /*
         * 「重複」的範圍是「每個使用者各自」，不是全域（ADR-017）。
         *
         * 這一條在保護 repository 方法名字裡的 AndUserId。
         * 少了它，第二個使用者會被第一個使用者的訂閱擋住。
         */
        String url = "https://shared.example.com/rss";

        createSource(ownerToken, "我的", url);
        createSource(otherToken, "別人的", url);

        assertThat(sourceCount(ownerToken)).isEqualTo(1);
        assertThat(sourceCount(otherToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("11. ★ 刪除之後可以重新訂閱同一個 url")
    void create_afterDelete_shouldSucceed() throws Exception {

        /*
         * 這一條在保護 repository 方法名字裡的 AndDeletedAtIsNull。
         *
         * 少了它，使用者刪掉一個來源就永遠加不回來——
         * 因為那筆 soft delete 的資料仍留在表中，會被當成重複。
         *
         * 資料庫端的 uq_source_user_url 也帶了 WHERE deleted_at IS NULL，
         * 兩層用的是同一個規則。
         */
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

        /*
         * 使用者複製網址列上的東西（首頁），不是 feed 網址。
         * FeedResolver 會從那頁的 <link rel="alternate"> 找出真正的 feed。
         *
         * 重點是「存進資料庫的是哪一個」——必須是 feed 網址，
         * 否則明天排程會拿首頁去解析，每天都失敗。
         */
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

        /*
         * ADR-017 的核心：不能用的來源根本進不了資料庫。
         *
         * 若允許存進去，使用者會以為訂閱成功，
         * 然後明天排程失敗，而那時候沒有人在看畫面。
         */
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

        // 光是回 400 不夠——要確認真的沒有存進去
        assertThat(sourceCount(ownerToken)).isZero();
    }

    @Test
    @DisplayName("14. ★ 看別人的來源的抓取紀錄 → 404")
    void fetchJobs_otherUsersSource_shouldReturnNotFound() throws Exception {

        /*
         * 這一條在防 IDOR（Broken Access Control）。
         *
         * fetch_job 表沒有 user_id（ADR-012 跨聚合只存 id），
         * 所以權限沒辦法像其他地方一樣寫進查詢條件。
         * 必須先確認 source 屬於這個使用者，才去撈它的紀錄。
         *
         * 少了那一步，任何人只要猜 sourceId 就能看到別人訂閱了什麼。
         */
        long id = createSource(ownerToken, "我的來源", "https://mine.example.com/rss");

        mockMvc.perform(get("/api/v1/sources/" + id + "/fetch-jobs")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("15. 剛建立的來源還沒被抓過 → 空清單，不是 404")
    void fetchJobs_newSource_shouldReturnEmptyList() throws Exception {

        /*
         * 「來源存在但還沒有抓取紀錄」和「來源不存在」是兩件事。
         * 前者回空陣列，後者回 404。
         */
        long id = createSource(ownerToken, "全新的來源", "https://brand-new.example.com/rss");

        mockMvc.perform(get("/api/v1/sources/" + id + "/fetch-jobs")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- 輔助方法 ----------

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
