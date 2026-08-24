package dev.sift.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件 CRUD 的整合測試。
 *
 * <p>測試案例由使用者在實作前列出（9D 的 test-first 練習），
 * 因此驗證的是「需求該有什麼」，而不是「程式碼做了什麼」。
 *
 * <p>建立兩個使用者是必要的：權限相關的測試沒有第二個人就測不出來。
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentFlowIntegrationTest {

    private static final String OWNER_EMAIL = "doc-owner@example.com";
    private static final String OTHER_EMAIL = "doc-other@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** 文件的擁有者。 */
    private String ownerToken;

    /** 另一個合法使用者——用來驗證他碰不到別人的東西。 */
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        ownerToken = registerAndLogin(OWNER_EMAIL);
        otherToken = registerAndLogin(OTHER_EMAIL);
    }

    // ---------- 1–2：建立 ----------

    @Test
    @DisplayName("1. 建立文件成功 → 201 且帶 Location")
    void create_shouldReturnCreated() throws Exception {

        mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"我的筆記","content":"內容"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("我的筆記"))
                .andExpect(jsonPath("$.origin").value("MANUAL"));
    }

    @Test
    @DisplayName("2. 標題為空白 → 400")
    void create_withBlankTitle_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","content":"內容"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    // ---------- 3–4：讀取 ----------

    @Test
    @DisplayName("3. 讀自己的文件 → 200，內容正確")
    void get_ownDocument_shouldReturnOk() throws Exception {

        long id = createDocument(ownerToken, "標題 A", "內文 A");

        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("標題 A"))
                .andExpect(jsonPath("$.content").value("內文 A"));
    }

    @Test
    @DisplayName("4. 讀別人的文件 → 404（不是 403，不洩漏它存在）")
    void get_otherUsersDocument_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "機密", "不該被看到");

        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // ---------- 5–7：刪除 ----------

    @Test
    @DisplayName("5. 刪除自己的文件 → 204")
    void delete_ownDocument_shouldReturnNoContent() throws Exception {

        long id = createDocument(ownerToken, "要刪掉的", "內容");

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("6. 刪除後再讀 → 404")
    void get_afterDelete_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "要刪掉的", "內容");

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. 刪除後列表少一筆，但資料庫那一列還在（soft delete）")
    void delete_shouldRemoveFromListButKeepRow() throws Exception {

        createDocument(ownerToken, "留著的", "內容");
        long deletedId = createDocument(ownerToken, "要刪掉的", "內容");

        assertThat(totalElements(ownerToken)).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/documents/" + deletedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(totalElements(ownerToken))
                .as("列表應該看不到已刪除的文件")
                .isEqualTo(1);

        /*
         * soft delete 的關鍵斷言：資料還在。
         *
         * findById 是 JpaRepository 內建的，不帶 deletedAt 條件——
         * 這是整個專案唯一適合用它的場合：驗證「查不到」不等於「被刪掉」。
         *
         * clear() 是為了避免讀到記憶體中的舊物件，強迫回資料庫確認。
         */
        entityManager.flush();
        entityManager.clear();

        Document row = documentRepository.findById(deletedId).orElseThrow();
        assertThat(row.getDeletedAt())
                .as("deleted_at 應該被填上，而不是整列消失")
                .isNotNull();
    }

    // ---------- 8–9：刪除的例外情況 ----------

    @Test
    @DisplayName("8. 刪別人的文件 → 404，且對方的文件安然無恙")
    void delete_otherUsersDocument_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "別人動不了", "內容");

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // 光是回 404 不夠——要確認它「真的沒被刪掉」
        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("9. 重複刪除同一篇 → 第二次 404")
    void delete_twice_shouldReturnNotFoundOnSecondAttempt() throws Exception {

        long id = createDocument(ownerToken, "刪兩次", "內容");

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // ---------- 10–11：列表與權限 ----------

    @Test
    @DisplayName("10. 列表只看得到自己的文件")
    void list_shouldOnlyContainOwnDocuments() throws Exception {

        createDocument(ownerToken, "我的 1", "內容");
        createDocument(ownerToken, "我的 2", "內容");
        createDocument(otherToken, "別人的", "內容");

        assertThat(totalElements(ownerToken)).isEqualTo(2);
        assertThat(totalElements(otherToken)).isEqualTo(1);
    }

    @Test
    @DisplayName("11. size 超過上限會被限制在 100")
    void list_withExcessiveSize_shouldBeCapped() throws Exception {

        String body = mockMvc.perform(get("/api/v1/documents?size=999999")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).get("size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("12. 未登入存取文件 API → 401")
    void anyEndpoint_withoutToken_shouldReturnUnauthorized() throws Exception {

        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/documents/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/documents/1")).andExpect(status().isUnauthorized());
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

    private long createDocument(String token, String title, String content) throws Exception {
        String body = mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s"}
                                """.formatted(title, content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private long totalElements(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("totalElements").asLong();
    }
}
