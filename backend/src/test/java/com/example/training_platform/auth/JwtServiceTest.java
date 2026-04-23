package com.example.training_platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;

import com.example.training_platform.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("01234567890123456789012345678901");
        props.setAccessTtlSeconds(300);
        props.setRefreshTtlSeconds(600);
        jwtService = new JwtService(props);
    }

    @Test
    void createAccessToken_containsTypeAccessAndSubject() {
        String token = jwtService.createAccessToken(42L, "user@example.com", "MENTOR");
        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("MENTOR");
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void createRefreshToken_containsTypeRefresh() {
        String token = jwtService.createRefreshToken(7L, "u@x.com", "TRAINEE");
        Claims claims = jwtService.parseClaims(token);
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
        assertThat(claims.getSubject()).isEqualTo("7");
    }

    @Test
    void parseClaims_rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parseClaims("not-a-jwt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid token");
    }
}
