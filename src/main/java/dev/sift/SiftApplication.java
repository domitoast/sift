package dev.sift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Sift 應用程式的進入點。
 *
 * <p>{@code @SpringBootApplication} 是三個註解的組合：
 *
 * <ul>
 *   <li>{@code @SpringBootConfiguration} — 標記這是設定類別</li>
 *   <li>{@code @EnableAutoConfiguration} — 依 classpath 上有哪些函式庫，
 *       自動推測並建立需要的 Bean。例如偵測到 PostgreSQL driver 與
 *       spring.datasource 設定，就自動建立 DataSource 與連線池</li>
 *   <li>{@code @ComponentScan} — 掃描這個類別所在的套件及其子套件，
 *       找出標了 {@code @Service}、{@code @Repository}、{@code @Component}
 *       等註解的類別，建立成 Bean</li>
 * </ul>
 *
 * <p><b>重要：</b>因為 {@code @ComponentScan} 是從這個類別的所在位置開始往下掃，
 * 所以這個類別必須放在**最上層的套件**（此處為 {@code dev.sift}）。
 * 若放進子套件（例如 {@code dev.sift.config}），
 * 位於 {@code dev.sift.domain} 的類別就掃不到，
 * 會出現「明明加了 @Service 卻找不到 Bean」的問題。
 */
@SpringBootApplication
/*
 * @ConfigurationPropertiesScan：掃描標了 @ConfigurationProperties 的類別
 * 並註冊成 Bean。
 *
 * 沒有這行的話，JwtProperties 不會被建立，注入時會找不到。
 * 另一種寫法是在某個 @Configuration 類別上標
 * @EnableConfigurationProperties(JwtProperties.class)，
 * 但那需要逐一列舉，之後每加一個設定類別都要改。
 */
@ConfigurationPropertiesScan
public class SiftApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiftApplication.class, args);
    }
}
