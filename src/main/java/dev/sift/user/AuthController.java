package dev.sift.user;

import dev.sift.auth.AuthService;
import dev.sift.auth.dto.LoginRequest;
import dev.sift.auth.dto.TokenResponse;
import dev.sift.user.dto.RegisterRequest;
import dev.sift.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 認證相關的 HTTP 入口。
 *
 * <p>{@code @RestController} 是兩個註解的合體：
 * <ul>
 *   <li>{@code @Controller} — 這是一個處理 HTTP 請求的 Bean</li>
 *   <li>{@code @ResponseBody} — 方法的回傳值直接轉成 JSON，
 *       而不是被當成「網頁樣板的名稱」</li>
 * </ul>
 *
 * <p><b>Controller 的職責只有三件事</b>：
 * <ol>
 *   <li>接收 HTTP 請求並轉成 Java 物件</li>
 *   <li>呼叫 Service</li>
 *   <li>把結果轉成 HTTP 回應</li>
 * </ol>
 * <b>不寫任何業務邏輯</b>。若這裡出現 if-else 判斷業務規則，
 * 就代表邏輯放錯層了。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    /**
     * 註冊新帳號。
     *
     * <p>{@code @Valid} 是關鍵：它觸發 RegisterRequest 上的
     * {@code @NotBlank}、{@code @Email}、{@code @Size} 檢查。
     * <b>沒有這個註解，那些驗證註解完全不會生效</b>——
     * 這是很常見的錯誤，而且不會有任何警告。
     *
     * <p>驗證失敗時 Spring 會丟 {@code MethodArgumentNotValidException}，
     * 由 {@link dev.sift.common.GlobalExceptionHandler} 統一轉成 400 回應。
     *
     * <p>{@code @RequestBody} 表示「請求的 JSON body 轉成這個物件」。
     *
     * <p>回傳 201 Created 而非 200 OK，並附上 {@code Location} 標頭
     * 指向新建立的資源——這是 REST 對「建立成功」的標準做法。
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {

        UserResponse response = userService.register(request);

        return ResponseEntity
                .created(URI.create("/api/v1/users/" + response.id()))
                .body(response);
    }

    /**
     * 登入，取得 access token。
     *
     * <p>成功回 200 OK（不是 201 Created）——
     * 這個請求沒有「建立」任何資源，只是換發一張憑證。
     *
     * <p>失敗時由 {@link dev.sift.common.GlobalExceptionHandler}
     * 統一轉成 401，且不透露是帳號錯還是密碼錯。
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
