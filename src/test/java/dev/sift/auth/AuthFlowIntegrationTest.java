package dev.sift.auth;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認證流程的整合測試：登入 → 換發 → 盜用偵測 → 登出。
 *
 * <p><b>為什麼是整合測試而不是單元測試</b>：
 * 這裡要驗證的東西全都跨越了多個層——filter 驗票、security 規則放行、
 * Service 判斷、資料庫實際被更新。單元測試把這些層都換成假的之後，
 * 剩下能驗證的只有「我呼叫了我以為我會呼叫的方法」，那沒有意義。
 *
 * <p>{@code @Transactional} 讓每個測試結束後自動回滾，
 * 因此測試之間不會互相污染，也不需要手動清理資料。
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    private static final String EMAIL = "flow@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /**
     * {@code @PersistenceContext} 注入 Hibernate 的 EntityManager。
     *
     * <p>需要它的唯一原因是 {@code clear()}——見盜用偵測那個測試的說明。
     */
    @PersistenceContext
    private EntityManager entityManager;

    /** 本次測試建立的使用者 id。所有斷言都只看這個人的資料。 */
    private Long userId;

    /**
     * 每個測試前先註冊一個使用者，並記下他的 id。
     *
     * <p><b>為什麼要記 id</b>：這個專案的測試和開發共用同一個資料庫，
     * 裡面可能殘留手動測試留下的資料。
     * 因此所有斷言都必須限定在「這個測試建立的使用者」範圍內，
     * <b>不能假設整張表是空的</b>。
     *
     * <p>正解是 Testcontainers（每次測試起一個全新的資料庫容器），
     * 已列入技術債，Day 18 處理。
     */
    @BeforeEach
    void registerUser() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        userId = objectMapper.readTree(body).get("id").asLong();
    }

    // ---------- 登入 ----------

    @Test
    @DisplayName("登入成功會同時回傳 access token 與 refresh token")
    void login_shouldReturnBothTokens() throws Exception {

        JsonNode body = login();

        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("refreshToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresInSeconds").asLong()).isPositive();
    }

    @Test
    @DisplayName("資料庫存的是 refresh token 的雜湊，不是 token 本身")
    void login_shouldPersistHashNotRawToken() throws Exception {

        String rawToken = login().get("refreshToken").asText();

        List<RefreshToken> stored = refreshTokenRepository.findAllByUserId(userId);

        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getTokenHash())
                .as("資料庫不該存原始 token")
                .isNotEqualTo(rawToken)
                .hasSize(64);
        assertThat(stored.getFirst().getPreviousTokenHash())
                .as("剛登入還沒換發過")
                .isNull();
    }

    // ---------- 受保護的 endpoint ----------

    @Test
    @DisplayName("帶有效 access token 可以讀取 /me")
    void me_withValidToken_shouldReturnUserData() throws Exception {

        String accessToken = login().get("accessToken").asText();

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("不帶 token 讀取 /me 會被擋下")
    void me_withoutToken_shouldReturnUnauthorized() throws Exception {

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("簽章不符的 token 讀取 /me 會被擋下")
    void me_withTamperedToken_shouldReturnUnauthorized() throws Exception {

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer aaa.bbb.ccc"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 換發 ----------

    @Test
    @DisplayName("換發會同時給出新的 access token 與新的 refresh token")
    void refresh_shouldIssueNewPair() throws Exception {

        JsonNode first = login();
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode refreshed = refresh(oldRefresh, status().isOk());

        assertThat(refreshed.get("accessToken").asText()).isNotBlank();
        assertThat(refreshed.get("refreshToken").asText())
                .as("rotation：refresh token 也要換一張新的")
                .isNotEqualTo(oldRefresh);
    }

    @Test
    @DisplayName("換發後舊的雜湊會被移到 previous_token_hash，且仍然只有一列")
    void refresh_shouldRotateInPlace() throws Exception {

        String oldRefresh = login().get("refreshToken").asText();
        String oldHash = refreshTokenRepository.findAllByUserId(userId).getFirst().getTokenHash();

        refresh(oldRefresh, status().isOk());

        List<RefreshToken> stored = refreshTokenRepository.findAllByUserId(userId);

        assertThat(stored)
                .as("原地更新，不新增列（ADR-011）")
                .hasSize(1);
        assertThat(stored.getFirst().getPreviousTokenHash()).isEqualTo(oldHash);
        assertThat(stored.getFirst().getTokenHash()).isNotEqualTo(oldHash);
    }

    @Test
    @DisplayName("★ 已經換發過的 refresh token 再次被使用 → 判定盜用，該使用者所有憑證全部作廢")
    void refresh_withAlreadyUsedToken_shouldDetectReuseAndRevokeAll() throws Exception {

        String stolenToken = login().get("refreshToken").asText();

        // 第一次使用：正常換發
        refresh(stolenToken, status().isOk());

        // 第二次使用同一張：模擬「小偷先用過了，本人隨後才用」
        refresh(stolenToken, status().isUnauthorized());

        /*
         * 作廢是靠批次 UPDATE 完成的（revokeAllByUserId），
         * 而批次 UPDATE 會繞過 Hibernate 的一級快取——
         * 記憶體裡那些物件不知道自己已經被改了。
         *
         * clear() 把快取清空，強迫下面的查詢真的回資料庫拿最新狀態。
         * 少了這一行，斷言會讀到過時的物件而誤判為失敗。
         */
        entityManager.clear();

        assertThat(refreshTokenRepository.findAllByUserId(userId))
                .isNotEmpty()
                .allMatch(RefreshToken::isRevoked, "所有憑證都應該被作廢");
    }

    @Test
    @DisplayName("完全不存在的 refresh token 換發會被拒絕")
    void refresh_withUnknownToken_shouldReturnUnauthorized() throws Exception {

        refresh("this-token-never-existed", status().isUnauthorized());
    }

    // ---------- 登出 ----------

    @Test
    @DisplayName("登出後，該 refresh token 不能再換發")
    void logout_shouldInvalidateRefreshToken() throws Exception {

        String refreshToken = login().get("refreshToken").asText();

        logout(refreshToken).andExpect(status().isNoContent());

        refresh(refreshToken, status().isUnauthorized());
    }

    @Test
    @DisplayName("重複登出仍然回 204——登出是 idempotent 的")
    void logout_shouldBeIdempotent() throws Exception {

        String refreshToken = login().get("refreshToken").asText();

        logout(refreshToken).andExpect(status().isNoContent());
        logout(refreshToken).andExpect(status().isNoContent());
        logout("never-existed").andExpect(status().isNoContent());
    }

    // ---------- 輔助方法 ----------

    private JsonNode login() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }

    private JsonNode refresh(String refreshToken, ResultMatcher expected) throws Exception {

        String body = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }

    private ResultActions logout(String refreshToken) throws Exception {

        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(refreshToken)));
    }
}
