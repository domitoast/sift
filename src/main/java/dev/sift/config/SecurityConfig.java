package dev.sift.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 的設定。
 *
 * <p>加入 spring-boot-starter-security 之後，Spring 會自動建立一套
 * 預設的安全規則：所有 endpoint 都需要登入、自動產生登入頁面。
 *
 * <p>只要我們自己提供一個 {@link SecurityFilterChain} Bean，
 * 預設的那套就不會生效（auto-configuration 的
 * {@code @ConditionalOnMissingBean} 機制）。
 *
 * <p>⚠️ <b>目前是暫時版本：所有請求都放行。</b>
 * 今天的目標只是讓登入 API 能回傳 token。
 * 明天會加入 JWT 驗證 filter 與實際的保護規則。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                /*
                 * 停用 CSRF 保護。
                 *
                 * CSRF（Cross-Site Request Forgery）攻擊的前提是
                 * 「瀏覽器會自動附帶 cookie」。我們用的是 JWT——
                 * token 由前端主動放進 Authorization 標頭，
                 * 瀏覽器不會自動附帶，因此這個攻擊面不存在。
                 *
                 * ⚠️ 若日後改用 cookie 儲存 token，就必須把它開回來。
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * STATELESS：不建立 HTTP session。
                 *
                 * 預設情況下 Spring Security 會為每個使用者建立 session
                 * 並存在伺服器記憶體。我們用 JWT，每個請求自帶身分，
                 * 完全不需要 session——留著只是浪費記憶體。
                 *
                 * 這也是「JWT 讓伺服器不用記狀態」那句話的實際落實。
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // TODO(Day 7)：改成只放行 /auth/**，其餘需要認證
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
