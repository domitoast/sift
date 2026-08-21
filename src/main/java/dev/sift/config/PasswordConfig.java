package dev.sift.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密碼雜湊器的設定。
 *
 * <p>{@code @Configuration} 標記這是一個「產生 Bean 的地方」，
 * {@code @Bean} 標記的方法其回傳值會被 Spring 收進 IoC 容器，
 * 之後任何地方都能注入使用。
 *
 * <p><b>為什麼需要這個檔案</b>：
 * {@code BCryptPasswordEncoder} 是 Spring Security 提供的類別，
 * 不是我們寫的，因此無法在它身上標 {@code @Component}。
 * 對於「第三方類別要變成 Bean」的情況，做法就是寫一個
 * {@code @Configuration} 類別，用 {@code @Bean} 方法把它產生出來。
 */
@Configuration
public class PasswordConfig {

    /**
     * 回傳型別刻意宣告為介面 {@code PasswordEncoder}，
     * 而非實作類別 {@code BCryptPasswordEncoder}。
     *
     * <p>這樣一來，注入它的 Service 只認識介面，不知道背後是 BCrypt。
     * 日後若要換成 Argon2（另一種更新的雜湊演算法），
     * 只需要改這一行，Service 完全不用動。
     *
     * <p>這就是 Day 4 教 DI 時說的「可替換」的具體體現。
     *
     * <p>cost factor 使用預設值 10（約 100 毫秒／次）。
     * 數字每加 1，運算時間加倍：
     * 11 約 200ms、12 約 400ms。
     * 太低不安全，太高會讓登入變慢並成為 DoS 攻擊的施力點。
     * 10～12 是目前的常見區間。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
