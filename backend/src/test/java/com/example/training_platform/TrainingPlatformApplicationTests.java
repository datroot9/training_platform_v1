package com.example.training_platform;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context requires a running MySQL instance matching {@code spring.datasource.url}.
 * Run manually when the database is available, or use a test profile with Testcontainers.
 */
@SpringBootTest
@Disabled("Requires MySQL; enable locally when DB is up")
class TrainingPlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}
