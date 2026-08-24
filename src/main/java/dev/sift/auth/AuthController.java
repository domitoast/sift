package dev.sift.auth;

import dev.sift.auth.dto.LoginRequest;
import dev.sift.auth.dto.RefreshRequest;
import dev.sift.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 憑證的簽發、換發與作廢。
 *
 * <p>只依賴 {@link AuthService}——這是 Day 10 拆分的目的：
 * 原本的 AuthController 同時依賴 UserService 與 AuthService，
 * 橫跨兩個領域，而且放在 {@code user} 套件裡。
 *
 * <p>這三支都在 {@code /api/v1/auth/**} 之下，屬於 permitAll。
 * 呼叫 refresh 的前提就是 access token 已經過期，
 * 若要求先通過認證才能換發，會形成「要有 token 才能換 token」的死鎖。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登入，取得 access token 與 refresh token。
     *
     * <p>成功回 200——這個請求沒有建立任何資源，只是換發憑證。
     * 失敗一律 401，且不透露是帳號錯還是密碼錯。
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 用 refresh token 換一張新的 access token，並輪替 refresh token。
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 登出：作廢這一張 refresh token。
     *
     * <p>永遠回 204，即使那張 token 早就無效——
     * idempotent，且不洩漏 token 的有效性。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
