package dev.sift.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled tasks. Disabled under the test profile so tests never
 * reach the network.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sift.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
