package dev.sift.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 註冊流程的整合測試。
 *
 * <p>與單元測試的差別：這裡<b>啟動了整個 Spring 應用程式</b>，
 * 走完 Controller → Service → Repository → 真實資料庫的完整路徑。
 *
 * <p>因此它能驗證單元測試看不到的東西：
 * JSON 序列化是否正確、驗證註解是否真的生效、
 * 例外處理器是否回傳正確的狀態碼、資料庫約束是否如預期。
 *
 * <p><b>前提：Docker 裡的 PostgreSQL 必須正在執行。</b>
 * 這是目前的弱點——測試依賴外部環境。
 * Day 18 會改用 Testcontainers，由測試自己啟動一個資料庫容器。
 *
 * <p>{@code @Transactional} 讓每個測試方法跑完自動 rollback，
 * 因此測試不會在開發用的資料庫留下垃圾資料。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    /**
     * MockMvc 讓我們在測試中模擬 HTTP 請求，
     * 不需要真的啟動一個網路 port。
     */
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
                // Location 標頭指向新資源，這是 REST 對 201 的標準做法
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.id").exists())
                /*
                 * 最重要的兩個斷言：確認敏感欄位「不存在」。
                 *
                 * 若日後有人不小心把 Entity 直接回傳，
                 * 或在 UserResponse 加了不該加的欄位，這兩行會立刻失敗。
                 */
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
        // BCrypt 的輸出格式：$2a$<成本因子>$<salt+雜湊>，總長 60
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

        // 第一次成功
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // 第二次應被拒絕
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
                /*
                 * 驗證「兩個欄位都被回報」。
                 *
                 * 這確認了 Bean Validation 會收集所有錯誤，
                 * 而不是遇到第一個就停——使用者才能一次修正完畢。
                 */
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

        // 用正規化後的小寫形式應該查得到
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("mixedcase@example.com"))
                .isPresent();
    }
}
