package com.example.training_platform.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.example.training_platform.auth.AuthService;
import com.example.training_platform.auth.PasswordHashService;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserDao userDao;
    @Mock
    private PasswordHashService passwordHashService;
    @Mock
    private AuthService authService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(userDao, passwordHashService, authService);
    }

    @Test
    void changePassword_throwsBadRequestWhenOldPasswordWrong() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setPasswordHash("stored");
        when(userDao.selectActiveUserById(7L)).thenReturn(Optional.of(user));
        when(passwordHashService.matches("old", "stored")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(7L, "old", "newpassword1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value()));

        verify(authService, never()).revokeAllRefreshTokens(any());
    }

    @Test
    void changePassword_updatesHashAndRevokesTokens() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setPasswordHash("stored");
        when(userDao.selectActiveUserById(7L)).thenReturn(Optional.of(user));
        when(passwordHashService.matches("old", "stored")).thenReturn(true);
        when(passwordHashService.hash("newpassword1")).thenReturn("newhash");

        accountService.changePassword(7L, "old", "newpassword1");

        assertThat(user.getPasswordHash()).isEqualTo("newhash");
        assertThat(user.isMustChangePassword()).isFalse();
        verify(userDao).update(user);
        verify(authService).revokeAllRefreshTokens(7L);
    }
}
