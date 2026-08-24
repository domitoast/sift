package dev.sift.config;

import dev.sift.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 的設定。
 *
 * <p>只要我們提供了自己的 {@link SecurityFilterChain} Bean，
 * Spring Boot 的預設安全設定就不會生效
 * （auto-configuration 的 {@code @ConditionalOnMissingBean} 機制）。
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                /*
                 * 停用 CSRF。
                 * CSRF 攻擊的前提是「瀏覽器自動附帶 cookie」，
                 * 而 JWT 由前端主動放進 Authorization 標頭，不會被自動附帶。
                 * ⚠️ 若日後改用 cookie 存 token，必須把它開回來。
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * 不建立 HTTP session。每個請求自帶身分，伺服器不記狀態。
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * ★ 授權規則。
                 *
                 * ⚠️ 順序極重要：Spring 由上往下比對，第一個符合的規則生效。
                 *    因此必須「從最特殊排到最一般」。
                 *    若 anyRequest() 寫在最上面，下面的規則永遠不會被讀到，
                 *    連登入都會被擋住。
                 */
                .authorizeHttpRequests(auth -> auth
                        // 註冊與登入：沒有帳號的人才會用，當然不能要求先登入
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 健康檢查：給部署平台與負載平衡器用的，必須公開
                        .requestMatchers("/actuator/health").permitAll()
                        // 其餘一律需要有效身分
                        .anyRequest().authenticated())

                /*
                 * 未通過認證時的處理。
                 *
                 * 預設行為是導向登入頁面（302 重新導向）——那是給網頁應用用的。
                 * 我們是純 API，應該直接回 401 讓呼叫端自己處理。
                 *
                 * 沒有這段設定的話，前端會收到一個奇怪的 302，
                 * 而不是預期中的 401。
                 */
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                /*
                 * ★ 把我們的 filter 插進 filter chain。
                 *
                 * addFilterBefore 的意思是「排在指定 filter 之前」。
                 *
                 * 為什麼是 UsernamePasswordAuthenticationFilter？
                 * 它是 Spring Security 內建的表單登入處理器，位置在
                 * 授權檢查之前。插在它前面，等於確保我們的 filter
                 * 在「決定要不要放行」之前就跑完。
                 *
                 * 順序錯了會怎樣：若插在授權檢查之後，
                 * 檢查時 SecurityContext 還是空的 → 所有請求都被擋。
                 */
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
