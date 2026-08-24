package dev.sift.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 把 Authorization 標頭裡的 JWT 翻譯成「這個請求是誰」。
 *
 * <p>這個 filter <b>只做認證（authentication，你是誰）</b>，
 * 不做授權（authorization，你能不能做這件事）。
 * 後者由 SecurityConfig 的規則決定。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * {@code @NonNull} 是 Spring 的標註，宣告「這個參數不會是 null」。
     * 它只影響 IDE 與靜態分析工具的提示，不產生執行時檢查。
     * 這裡加上是為了與父類別的簽章一致，避免 IDE 警告。
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        /*
         * 第二個條件 getAuthentication() == null 是必要的防護：
         * 若前面已經有其他機制設定過身分，不應該被覆蓋。
         * 目前沒有其他機制，但這是標準寫法。
         */
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(token, request);
        }

        /*
         * ⚠️ 這一行「必須」執行，而且必須在所有路徑上都執行。
         *
         * 它的意思是「把請求交給下一個 filter」。
         * 忘記呼叫的話，請求會停在這裡——使用者收到空白回應，
         * Controller 完全不會被執行，而且沒有任何錯誤訊息。
         *
         * 這是寫 filter 最常見的錯誤。
         */
        filterChain.doFilter(request, response);
    }

    /**
     * 從 {@code Authorization: Bearer xxx} 取出 token 部分。
     *
     * @return token 字串；標頭不存在或格式不符時回傳 null
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);

        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        return header.substring(PREFIX.length());
    }

    /**
     * 驗證 token 並把身分放進 SecurityContext。
     *
     * <p><b>驗證失敗時刻意不拋例外、不回應 401</b>，只是靜靜地什麼都不做。
     *
     * <p>理由：「該不該擋」是 SecurityConfig 的規則要決定的事。
     * 若這裡直接回 401，那麼所有的 401 會有兩個來源、兩種格式。
     * 讓身分保持空白，後面的 AuthorizationFilter 會統一處理。
     *
     * <p>這是單一職責的實際應用：filter 認人，授權規則決定放不放行。
     */
    private void authenticate(String token, HttpServletRequest request) {
        try {
            Long userId = jwtService.extractUserId(token);

            /*
             * UsernamePasswordAuthenticationToken 是 Spring Security 內建的
             * 「身分憑證」實作。名字有 Password 是歷史因素——
             * 它最初為表單登入設計，但也適用於任何已驗證的身分。
             *
             * 三個參數：
             *   principal   — 「誰」。這裡放 userId，Controller 可用
             *                 @AuthenticationPrincipal 取得
             *   credentials — 「憑什麼」。已經驗證過了，放 null 即可，
             *                 而且不該把 token 留在記憶體裡
             *   authorities — 「有哪些角色權限」。本專案不做 RBAC，給空清單
             *
             * ⚠️ 關鍵：只有「使用這個三參數建構子」產生的物件，
             *    isAuthenticated() 才會是 true。
             *    兩參數的版本會是 false，導致明明驗證成功卻仍被擋下。
             *    這是很常見的踩雷點。
             */
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());

            // 記錄請求來源 IP 等資訊，供稽核使用
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException e) {
            /*
             * 涵蓋簽章不符、token 過期、格式錯誤等所有情況。
             *
             * 用 debug 等級而非 warn：無效 token 是很常見的正常現象
             * （token 過期、使用者換裝置），用 warn 會把日誌淹沒。
             *
             * 只記錄例外訊息，不記錄 token 本身——token 是憑證，
             * 不該出現在日誌裡。
             */
            log.debug("JWT 驗證失敗：{}", e.getMessage());
        }
    }
}
