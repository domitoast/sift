package dev.sift.user;

import dev.sift.support.PostgresTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserRegistrationIntegrationTest extends PostgresTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /register 成功時回 201，且回應不含密碼欄位")
    void register_shouldReturn201_andNotExposePassword() throws Exception {
        String body = """
                {
                  "email": "newuser@example.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())

                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.id").exists())

                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.llmApiKeyEncrypted").doesNotExist());
    }

    @Test
    @DisplayName("密碼在資料庫中應為 BCrypt 雜湊，而非明文")
    void register_shouldPersistHashedPassword() throws Exception {
        String body = """
                {
                  "email": "hashcheck@example.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmailAndDeletedAtIsNull("hashcheck@example.com")
                .orElseThrow();

        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");

        assertThat(saved.getPasswordHash()).startsWith("$2a$");
        assertThat(saved.getPasswordHash()).hasSize(60);
    }

    @Test
    @DisplayName("重複的 email 應回 409，並使用 RFC 7807 格式")
    void register_shouldReturn409_whenEmailDuplicated() throws Exception {
        String body = """
                {
                  "email": "duplicate@example.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.type").value("https://sift.dev/errors/email-already-used"));
    }

    @Test
    @DisplayName("輸入不合法時應回 400，並逐欄位列出錯誤")
    void register_shouldReturn400_withFieldErrors() throws Exception {
        String body = """
                {
                  "email": "not-an-email",
                  "password": "123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))

                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    @DisplayName("email 大小寫與空白應被正規化後才儲存")
    void register_shouldNormalizeEmailBeforePersisting() throws Exception {
        String body = """
                {
                  "email": "  MixedCase@Example.COM  ",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailAndDeletedAtIsNull("mixedcase@example.com"))
                .isPresent();
    }
}
