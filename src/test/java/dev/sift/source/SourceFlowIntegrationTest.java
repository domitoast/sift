package dev.sift.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
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

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
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
