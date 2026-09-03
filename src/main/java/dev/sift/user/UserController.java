package dev.sift.user;

import dev.sift.user.dto.RegisterRequest;
import dev.sift.user.dto.SetLlmApiKeyRequest;
import dev.sift.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 使用者本身的 HTTP 入口：建立帳號、讀取自己的資料。
 *
 * <p><b>沒有 class 層的 {@code @RequestMapping}</b>，兩個方法各自寫完整路徑。
 *
 * <p>原因是這兩支目前的路徑前綴不同：
 * {@code /api/v1/auth/register} 與 {@code /api/v1/me}。
 *
 * <p>⚠️ {@code register} 掛在 {@code /auth/} 之下其實不太對——
 * 「建立使用者」是 user 領域的操作，不是認證。
 * RESTful 的寫法會是 {@code POST /api/v1/users}。
 *
 * <p>今天刻意<b>不改路徑</b>：Day 10 這一段是純搬移，
 * 搬移出錯時必須能確定「是搬壞的，不是改壞的」。路徑要不要改另外決定。
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 註冊新帳號。
     *
     * <p>{@code @Valid} 觸發 RegisterRequest 上的驗證。
     * <b>沒有這個註解，那些驗證完全不會生效，而且沒有任何警告。</b>
     *
     * <p>回 201 Created 並附上 Location 標頭指向新資源。
     */
    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {

        UserResponse response = userService.register(request);

        return ResponseEntity
                .created(URI.create("/api/v1/users/" + response.id()))
                .body(response);
    }

    /**
     * 回傳當前登入者的資料。
     *
     * <p>{@code @AuthenticationPrincipal} 取出 SecurityContext 裡的身分，
     * 那是 {@code JwtAuthenticationFilter} 驗證簽章之後放進去的。
     *
     * <p>⚠️ 絕對不可改成從請求內容取得使用者身分——那是 IDOR。
     */
    @GetMapping("/api/v1/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Long userId) {

        UserResponse response = userService.findById(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * 設定或更新自己的 LLM API key（ADR-003 BYOK）。
     *
     * <p><b>為什麼是 PUT 而不是 PATCH</b>：這裡只有一個欄位，
     * 而且語意是「整個換掉」——沒有「只改一半」這種事。
     * PATCH 適合「多個欄位，只改我有給的那些」，例如 {@code PATCH /sources/{id}}。
     *
     * <p><b>為什麼路徑是 {@code /me/llm-key} 而不是 {@code /users/{id}/llm-key}</b>：
     * 使用者只能改自己的。路徑裡放 id 等於邀請別人去試別的 id——
     * 那是 IDOR 的溫床。身分一律從 token 取得。
     *
     * <p>回應包含遮罩形式，讓使用者確認貼對了。
     */
    @PutMapping("/api/v1/me/llm-key")
    public ResponseEntity<UserResponse> updateLlmApiKey(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SetLlmApiKeyRequest request) {

        return ResponseEntity.ok(userService.updateLlmApiKey(userId, request.apiKey()));
    }

    /**
     * 移除自己的 LLM API key。
     *
     * <p>回 204 No Content：操作成功，但沒有內容要回傳。
     *
     * <p>移除之後文章會停在 {@code NEW}，不算失敗（ADR-003）。
     */
    @DeleteMapping("/api/v1/me/llm-key")
    public ResponseEntity<Void> clearLlmApiKey(@AuthenticationPrincipal Long userId) {

        userService.clearLlmApiKey(userId);

        return ResponseEntity.noContent().build();
    }
}
