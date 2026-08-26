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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    // ---------- 13–16：編輯與 optimistic lock ----------

    @Test
    @DisplayName("13. 編輯自己的文件 → 200，version 遞增")
    void update_shouldSucceedAndIncrementVersion() throws Exception {

        long id = createDocument(ownerToken, "原標題", "原內容");

        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"新標題","content":"新內容","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("新標題"))
                /*
                 * 這一條斷言就是在保護 Service 裡那行 flush()。
                 *
                 * 少了 flush，回應會帶著更新前的 version（0），
                 * 使用者拿著它再送一次就會收到 409——而那個「別人」是他自己。
                 */
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("14. ★ 帶著過期的 version 編輯 → 409，且回報目前版本")
    void update_withStaleVersion_shouldReturnConflict() throws Exception {

        long id = createDocument(ownerToken, "會議記錄", "1. 預算");

        // 同事先儲存（他讀到的是 version 0）
        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"會議記錄","content":"1. 預算 / 2. 時程","version":0}
                                """))
                .andExpect(status().isOk());

        // 我後儲存，但手上還是 version 0——我沒看到同事的修改
        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"會議記錄","content":"1. 預算 / 3. 人力","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentVersion").value(1));
    }

    @Test
    @DisplayName("15. 衝突發生時，先寫入者的內容必須完好無損")
    void update_whenConflict_shouldNotOverwrite() throws Exception {

        long id = createDocument(ownerToken, "會議記錄", "1. 預算");

        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"會議記錄","content":"1. 預算 / 2. 時程","version":0}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"會議記錄","content":"1. 預算 / 3. 人力","version":0}
                                """))
                .andExpect(status().isConflict());

        /*
         * 光是回 409 不夠——要確認資料「真的沒被覆蓋」。
         *
         * 這與測試 8（刪別人的文件）是同一個原則：
         * 「他被拒絕」和「東西真的沒事」是兩件事。
         */
        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("1. 預算 / 2. 時程"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("16. 編輯別人的文件 → 404（版本正確也不行）")
    void update_otherUsersDocument_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "別人動不了", "內容");

        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"被竄改","content":"被竄改","version":0}
                                """))
                .andExpect(status().isNotFound());

        // 確認原文完好
        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.title").value("別人動不了"));
    }

    @Test
    @DisplayName("17. 編輯時未提供 version → 400（不能繞過衝突偵測）")
    void update_withoutVersion_shouldReturnBadRequest() throws Exception {

        long id = createDocument(ownerToken, "標題", "內容");

        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"新標題","content":"新內容"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ---------- 18–24：版本歷史 ----------

    @Test
    @DisplayName("18. 建立文件後就有第 1 版，內容 = 建立時的內容")
    void create_shouldCreateInitialVersion() throws Exception {

        long id = createDocument(ownerToken, "週會", "1. 預算");

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[0].title").value("週會"))
                // 列表不含內文
                .andExpect(jsonPath("$[0].content").doesNotExist());
    }

    @Test
    @DisplayName("19. 編輯後版本歷史變成兩筆，最新的在前")
    void update_shouldAppendVersion() throws Exception {

        long id = createDocument(ownerToken, "週會", "1. 預算");
        updateDocument(ownerToken, id, "週會記錄", "1. 預算 / 2. 時程", 0);

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));
    }

    @Test
    @DisplayName("20. ★ 舊版本保留的是「當時」的內容，不是現在的")
    void getVersion_shouldReturnSnapshotAtThatTime() throws Exception {

        long id = createDocument(ownerToken, "週會", "1. 預算");
        updateDocument(ownerToken, id, "週會記錄", "1. 預算 / 2. 時程", 0);

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions/1")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("週會"))
                .andExpect(jsonPath("$.content").value("1. 預算"));

        // 對照組：文件本身已經是新的
        mockMvc.perform(get("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.title").value("週會記錄"));
    }

    @Test
    @DisplayName("21. 別人看不到你的版本歷史與單一版本 → 404")
    void versions_ofOtherUsersDocument_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "機密", "不該被看到");

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions/1")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("22. 查不存在的版本號 → 404")
    void getVersion_withUnknownNumber_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "標題", "內容");

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions/99")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("23. 文件被刪除後，版本歷史也查不到 → 404")
    void versions_afterDocumentDeleted_shouldReturnNotFound() throws Exception {

        long id = createDocument(ownerToken, "要刪掉的", "內容");

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        /*
         * 權限檢查先於版本查詢：requireOwnedDocument 用的是
         * findByIdAndUserIdAndDeletedAtIsNull，已刪除的文件查不到，
         * 所以連帶查不到它的版本——不需要在版本這一層寫任何額外判斷。
         */
        mockMvc.perform(get("/api/v1/documents/" + id + "/versions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("24. ★ 版本數超過 20 之後會被修剪，最舊的真的被刪掉")
    void versions_shouldBePrunedToLimit() throws Exception {

        long id = createDocument(ownerToken, "初版", "內容 0");

        /*
         * 編輯 20 次 → 版本歷史 = 1（初版）+ 20 = 21 版 → 修剪後剩 20 版。
         *
         * 每次帶的 version 是 document 的 optimistic lock 計數器，
         * 從 0 開始每次 +1，與 version_number（從 1 開始）差一。
         */
        for (int i = 0; i < 20; i++) {
            updateDocument(ownerToken, id, "第 " + (i + 1) + " 次", "內容 " + (i + 1), i);
        }

        mockMvc.perform(get("/api/v1/documents/" + id + "/versions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].versionNumber").value(21))
                .andExpect(jsonPath("$[19].versionNumber").value(2));

        /*
         * 光是「剩 20 筆」不夠——要確認第 1 版「真的被刪掉」。
         *
         * 若修剪的減法差一，可能剩下的是 1..20 而不是 2..21，
         * 上面的斷言會過，但這一條不會。
         */
        mockMvc.perform(get("/api/v1/documents/" + id + "/versions/1")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // ---------- 25–29：搜尋 ----------

    @Test
    @DisplayName("25. 用關鍵字搜尋標題")
    void search_shouldFindMatchingTitles() throws Exception {

        createDocument(ownerToken, "週會記錄", "內容");
        createDocument(ownerToken, "週會待辦", "內容");
        createDocument(ownerToken, "讀書筆記", "內容");

        assertThat(totalElements(ownerToken, "週會")).isEqualTo(2);
        assertThat(totalElements(ownerToken, "筆記")).isEqualTo(1);
        assertThat(totalElements(ownerToken, "不存在的關鍵字")).isZero();
    }

    @Test
    @DisplayName("26. ★ 搜尋只找得到自己的——即使別人有同名文件")
    void search_shouldOnlyReturnOwnDocuments() throws Exception {

        createDocument(ownerToken, "機密報告", "我的");
        createDocument(otherToken, "機密報告", "別人的");

        /*
         * 兩個人的文件標題一模一樣。
         *
         * 這個測試的價值在於：如果哪天有人把 userId 條件從搜尋查詢裡拿掉，
         * 「搜得到」那條測試照樣會過——只有這一條會紅。
         */
        assertThat(totalElements(ownerToken, "機密報告")).isEqualTo(1);
        assertThat(totalElements(otherToken, "機密報告")).isEqualTo(1);
    }

    @Test
    @DisplayName("27. 已刪除的文件搜不到")
    void search_shouldNotReturnDeletedDocuments() throws Exception {

        long id = createDocument(ownerToken, "要刪掉的報告", "內容");

        assertThat(totalElements(ownerToken, "報告")).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(totalElements(ownerToken, "報告")).isZero();
    }

    @Test
    @DisplayName("28. q 是空白時等於列出全部，不是零筆")
    void search_withBlankKeyword_shouldReturnAll() throws Exception {

        createDocument(ownerToken, "甲", "內容");
        createDocument(ownerToken, "乙", "內容");

        /*
         * 若 Service 沒有把空白視為「沒有關鍵字」，
         * 這裡會變成 LIKE '%%'——結果數字一樣，但那條查詢用不到索引，
         * 等於把「列出全部」偷偷變成掃描。
         */
        assertThat(totalElements(ownerToken, "")).isEqualTo(2);
        assertThat(totalElements(ownerToken, "   ")).isEqualTo(2);
    }

    @Test
    @DisplayName("29. 搜尋不分大小寫")
    void search_shouldBeCaseInsensitive() throws Exception {

        createDocument(ownerToken, "Meeting Notes", "內容");

        assertThat(totalElements(ownerToken, "meeting")).isEqualTo(1);
        assertThat(totalElements(ownerToken, "MEETING")).isEqualTo(1);
        assertThat(totalElements(ownerToken, "Notes")).isEqualTo(1);
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

    private void updateDocument(String token, long id, String title, String content, int version)
            throws Exception {

        mockMvc.perform(put("/api/v1/documents/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s","version":%d}
                                """.formatted(title, content, version)))
                .andExpect(status().isOk());
    }

    private long totalElements(String token) throws Exception {
        return totalElements(token, null);
    }

    /**
     * @param keyword 搜尋關鍵字；傳 null 代表不帶 {@code q} 參數
     */
    private long totalElements(String token, String keyword) throws Exception {

        var request = get("/api/v1/documents").header("Authorization", "Bearer " + token);

        if (keyword != null) {
            request = request.param("q", keyword);
        }

        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        return json.get("totalElements").asLong();
    }
}
