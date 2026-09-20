package dev.sift.auth;

import dev.sift.support.PostgresTestBase;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest extends PostgresTestBase {
    private static final String EMAIL = "flow@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long userId;

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

        refresh(stolenToken, status().isOk());

        refresh(stolenToken, status().isUnauthorized());

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
