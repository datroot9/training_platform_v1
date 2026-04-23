package com.example.training_platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import com.example.training_platform.dao.RefreshTokenDao;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.projection.AuthUserProjection;
import com.example.training_platform.entity.UserEntity;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "trainee@local";
    private static final String PASSWORD = "correct-password";

    @Mock
    private UserDao userDao;
    @Mock
    private RefreshTokenDao refreshTokenDao;
    @Mock
    private JwtService jwtService;
    @Mock
    private PasswordHashService passwordHashService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userDao, refreshTokenDao, jwtService, passwordHashService);
    }

    @Test
    void login_throwsUnauthorizedWhenEmailUnknown() {
        when(userDao.selectAuthUserByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value()));

        verify(passwordHashService, never()).matches(anyString(), anyString());
    }

    @Test
    void login_throwsForbiddenWhenInactive() {
        AuthUserProjection row = inactiveUserProjection("hash");
        when(userDao.selectAuthUserByEmail(EMAIL)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void login_throwsUnauthorizedWhenPasswordWrong() {
        AuthUserProjection row = activeUserProjection("$2a$10$hash");
        when(userDao.selectAuthUserByEmail(EMAIL)).thenReturn(Optional.of(row));
        when(passwordHashService.matches(PASSWORD, row.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void login_upgradesNonBcryptHash() {
        String legacyHash = "deadbeef";
        AuthUserProjection row = activeUserProjection(legacyHash);
        when(userDao.selectAuthUserByEmail(EMAIL)).thenReturn(Optional.of(row));
        when(passwordHashService.matches(PASSWORD, legacyHash)).thenReturn(true);
        when(passwordHashService.isBcrypt(legacyHash)).thenReturn(false);
        when(passwordHashService.hash(PASSWORD)).thenReturn("$2a$10$upgraded");
        UserEntity persisted = new UserEntity();
        persisted.setId(row.getId());
        when(userDao.selectById(row.getId())).thenReturn(Optional.of(persisted));
        stubTokenIssuance();

        var response = authService.login(EMAIL, PASSWORD);

        assertThat(response.userId()).isEqualTo(row.getId());
        verify(userDao).update(persisted);
        verify(refreshTokenDao).insert(any());
    }

    @Test
    void login_successWithoutUpgradeWhenBcrypt() {
        String bcrypt = new BCryptPasswordEncoder().encode(PASSWORD);
        AuthUserProjection row = activeUserProjection(bcrypt);
        when(userDao.selectAuthUserByEmail(EMAIL)).thenReturn(Optional.of(row));
        when(passwordHashService.matches(PASSWORD, bcrypt)).thenReturn(true);
        when(passwordHashService.isBcrypt(bcrypt)).thenReturn(true);
        stubTokenIssuance();

        var response = authService.login(EMAIL, PASSWORD);

        assertThat(response.accessToken()).isEqualTo("ACCESS_JWT");
        verify(userDao, never()).update(any());
        verify(refreshTokenDao).insert(any());
    }

    @Test
    void refresh_throwsWhenTokenTypeNotRefresh() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(jwtService.parseClaims("tok")).thenReturn(claims);

        assertThatThrownBy(() -> authService.refresh("tok"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void refresh_throwsWhenTokenRevokedOrExpired() {
        Claims claims = refreshClaims(5L);
        when(jwtService.parseClaims("tok")).thenReturn(claims);
        when(passwordHashService.sha256("tok")).thenReturn("h");
        when(refreshTokenDao.countValidToken(5L, "h")).thenReturn(0L);

        assertThatThrownBy(() -> authService.refresh("tok"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    @Test
    void refresh_throwsForbiddenWhenUserInactive() {
        Claims claims = refreshClaims(5L);
        when(jwtService.parseClaims("tok")).thenReturn(claims);
        when(passwordHashService.sha256("tok")).thenReturn("h");
        when(refreshTokenDao.countValidToken(5L, "h")).thenReturn(1L);
        when(refreshTokenDao.selectActiveToken(5L, "h")).thenReturn(Optional.empty());

        AuthUserProjection inactive = inactiveUserProjection("x");
        when(userDao.selectAuthUserById(5L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> authService.refresh("tok"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void refresh_success() {
        Claims claims = refreshClaims(5L);
        when(jwtService.parseClaims("tok")).thenReturn(claims);
        when(passwordHashService.sha256("tok")).thenReturn("h");
        when(refreshTokenDao.countValidToken(5L, "h")).thenReturn(1L);
        when(refreshTokenDao.selectActiveToken(5L, "h")).thenReturn(Optional.empty());

        AuthUserProjection active = activeUserProjection(new BCryptPasswordEncoder().encode("p"));
        when(userDao.selectAuthUserById(5L)).thenReturn(Optional.of(active));
        stubTokenIssuance();

        var response = authService.refresh("tok");

        assertThat(response.refreshToken()).isEqualTo("REFRESH_JWT");
        verify(refreshTokenDao).insert(any());
    }

    private void stubTokenIssuance() {
        when(jwtService.createAccessToken(anyLong(), anyString(), anyString())).thenReturn("ACCESS_JWT");
        when(jwtService.createRefreshToken(anyLong(), anyString(), anyString())).thenReturn("REFRESH_JWT");

        Claims refreshClaims = org.mockito.Mockito.mock(Claims.class);
        when(refreshClaims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(jwtService.parseClaims("REFRESH_JWT")).thenReturn(refreshClaims);

        Claims accessClaims = org.mockito.Mockito.mock(Claims.class);
        when(accessClaims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(600)));
        when(jwtService.parseClaims("ACCESS_JWT")).thenReturn(accessClaims);

        when(passwordHashService.sha256("REFRESH_JWT")).thenReturn("refresh-hash");
    }

    private static Claims refreshClaims(long userId) {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        return claims;
    }

    private static AuthUserProjection activeUserProjection(String passwordHash) {
        AuthUserProjection p = new AuthUserProjection();
        p.setId(99L);
        p.setEmail(EMAIL);
        p.setRole("TRAINEE");
        p.setPasswordHash(passwordHash);
        p.setActive(true);
        p.setMustChangePassword(false);
        return p;
    }

    private static AuthUserProjection inactiveUserProjection(String passwordHash) {
        AuthUserProjection p = activeUserProjection(passwordHash);
        p.setActive(false);
        return p;
    }
}
