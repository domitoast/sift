package dev.sift.user;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LLM API key 的設定與保護（ADR-003 BYOK）。
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LlmApiKeyIntegrationTest {

    private static final String EMAIL = "llmkey@example.com";
    private static final String PASSWORD = "password123";
    private static final String API_KEY = "sk-ant-api03-abcdefghijklmnop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String token;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        String credentials = """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD);

        String registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        userId = objectMapper.readTree(registered).get("id").asLong();

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        token = objectMapper.readTree(body).get("accessToken").asText();
    }

    private void setKey(String apiKey) throws Exception {
        mockMvc.perform(put("/api/v1/me/llm-key")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apiKey":"%s"}
                                """.formatted(apiKey)))
                .andExpect(status().isOk());
    }

    // ---------- 基本流程 ----------

    @Test
    @DisplayName("1. 剛註冊的帳號沒有 key → llmApiKeyMasked 是 null")
    void newUser_shouldHaveNoKey() throws Exception {

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmApiKeyMasked").doesNotExist());
    }

    @Test
    @DisplayName("2. 設定後，GET /me 看得到遮罩形式")
    void setKey_shouldReturnMasked() throws Exception {

        setKey(API_KEY);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.llmApiKeyMasked").value("sk-a...mnop"));
    }

    // ---------- ADR-003 的三條規定 ----------

    @Test
    @DisplayName("3. ★★ 資料庫裡存的不是明文")
    void setKey_shouldBeEncryptedInDatabase() throws Exception {

        /*
         * ADR-003 第一條：API key 不得明文儲存。
         *
         * 這一題直接去資料庫看那個欄位——不是看 API 的回應。
         * 只驗 API 回應是不夠的：加密壞掉時 API 一樣會回遮罩，
         * 但資料庫裡躺著的是明文。
         */
        setKey(API_KEY);

        String stored = userRepository.findById(userId).orElseThrow().getLlmApiKeyEncrypted();

        assertThat(stored).isNotNull();
        assertThat(stored).isNotEqualTo(API_KEY);
        assertThat(stored).doesNotContain("sk-ant");
    }

    @Test
    @DisplayName("4. ★★ 回應裡不能出現完整的 key")
    void setKey_responseShouldNotContainFullKey() throws Exception {

        /*
         * ADR-003 第二條：API key 不得回傳前端，只能回傳遮罩形式。
         *
         * 檢查整個回應字串，不是只檢查某個欄位——
         * 萬一有人不小心多加了一個欄位，這題會抓到。
         */
        String response = mockMvc.perform(put("/api/v1/me/llm-key")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apiKey":"%s"}
                                """.formatted(API_KEY)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(API_KEY);
        assertThat(response).contains("sk-a...mnop");
    }

    @Test
    @DisplayName("5. ★★ 請求 DTO 的 toString 不會洩漏 key")
    void requestToString_shouldNotLeak() {

        /*
         * ADR-003 第三條：API key 不得出現在任何日誌中。
         *
         * record 預設的 toString 會印出所有欄位，
         * 只要有任何一行 log.info("request={}", request) 就會外洩。
         *
         * SetLlmApiKeyRequest 覆寫掉了它。這一題盯著那個覆寫。
         */
        var request = new dev.sift.user.dto.SetLlmApiKeyRequest(API_KEY);

        assertThat(request.toString()).doesNotContain(API_KEY);
        assertThat(request.toString()).contains("***");
    }

    // ---------- 更新與移除 ----------

    @Test
    @DisplayName("6. 重新設定會覆蓋舊的")
    void setKey_twice_shouldOverwrite() throws Exception {

        setKey(API_KEY);
        setKey("sk-ant-api03-zzzzzzzzzzzzzzzz");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.llmApiKeyMasked").value("sk-a...zzzz"));
    }

    @Test
    @DisplayName("7. 移除後回到未設定狀態")
    void deleteKey_shouldClear() throws Exception {

        setKey(API_KEY);

        mockMvc.perform(delete("/api/v1/me/llm-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.llmApiKeyMasked").doesNotExist());

        assertThat(userRepository.findById(userId).orElseThrow().getLlmApiKeyEncrypted()).isNull();
    }

    // ---------- 驗證與權限 ----------

    @Test
    @DisplayName("8. 空的 key → 400")
    void setKey_blank_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(put("/api/v1/me/llm-key")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apiKey":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("9. 未登入 → 401")
    void llmKeyEndpoints_withoutToken_shouldReturnUnauthorized() throws Exception {

        mockMvc.perform(put("/api/v1/me/llm-key")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/me/llm-key")).andExpect(status().isUnauthorized());
    }
}
