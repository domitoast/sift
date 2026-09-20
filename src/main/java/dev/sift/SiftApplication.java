package dev.sift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)

@ConfigurationPropertiesScan
public class SiftApplication {
    public static void main(String[] args) {
        SpringApplication.run(SiftApplication.class, args);
    }
}
