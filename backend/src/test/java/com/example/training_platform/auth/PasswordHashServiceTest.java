package com.example.training_platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordHashServiceTest {

    private PasswordHashService passwordHashService;

    @BeforeEach
    void setUp() {
        passwordHashService = new PasswordHashService(new BCryptPasswordEncoder());
    }

    @Test
    void hashProducesBcryptThatMatchesRawPassword() {
        String hash = passwordHashService.hash("mySecret12");
        assertThat(passwordHashService.isBcrypt(hash)).isTrue();
        assertThat(passwordHashService.matches("mySecret12", hash)).isTrue();
        assertThat(passwordHashService.matches("other", hash)).isFalse();
    }

    @Test
    void matchesReturnsFalseForBlankStoredHash() {
        assertThat(passwordHashService.matches("x", null)).isFalse();
        assertThat(passwordHashService.matches("x", "   ")).isFalse();
    }

    @Test
    void matchesUsesSha256WhenStoredHashIsNotBcrypt() {
        String shaHex = passwordHashService.sha256("legacy");
        assertThat(passwordHashService.isBcrypt(shaHex)).isFalse();
        assertThat(passwordHashService.matches("legacy", shaHex)).isTrue();
        assertThat(passwordHashService.matches("legacy", shaHex.toUpperCase())).isTrue();
    }

    @Test
    void sha256IsDeterministic() {
        assertThat(passwordHashService.sha256("hello"))
                .isEqualTo(passwordHashService.sha256("hello"));
    }
}
