package com.example.training_platform.trainee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.example.training_platform.auth.PasswordHashService;
import com.example.training_platform.dao.UserDao;
import com.example.training_platform.dao.projection.UserBasicProjection;
import com.example.training_platform.entity.UserEntity;
import com.example.training_platform.trainee.dto.CreateTraineeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.seasar.doma.jdbc.Sql;
import org.seasar.doma.jdbc.SqlKind;
import org.seasar.doma.jdbc.SqlLogType;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TraineeServiceTest {

    @Mock
    private UserDao userDao;
    @Mock
    private PasswordHashService passwordHashService;

    private TraineeService traineeService;

    @BeforeEach
    void setUp() {
        traineeService = new TraineeService(userDao, passwordHashService);
    }

    @Test
    void setActive_throwsNotFoundWhenTraineeNotLinkedToMentor() {
        when(userDao.countTraineeByMentor(1L, 99L)).thenReturn(0L);

        assertThatThrownBy(() -> traineeService.setActive(1L, 99L, false))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));

        verify(userDao, never()).selectById(any());
    }

    @Test
    void create_throwsConflictOnDuplicateEmail() {
        when(passwordHashService.hash(any())).thenReturn("hash");
        @SuppressWarnings("rawtypes")
        Sql sql = Mockito.mock(Sql.class);
        Mockito.when(sql.getKind()).thenReturn(SqlKind.INSERT);
        Mockito.doThrow(new UniqueConstraintException(SqlLogType.RAW, sql, new RuntimeException("dup")))
                .when(userDao).insert(any(UserEntity.class));

        assertThatThrownBy(() -> traineeService.create(1L, new CreateTraineeRequest("dup@local", "Name")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value()));
    }

    @Test
    void resetPassword_updatesUserWhenTraineeExists() {
        UserBasicProjection projection = new UserBasicProjection();
        projection.setId(5L);
        projection.setEmail("t@local");
        when(userDao.findTraineeBasic(1L, 5L)).thenReturn(Optional.of(projection));
        UserEntity entity = new UserEntity();
        entity.setId(5L);
        when(userDao.selectById(5L)).thenReturn(Optional.of(entity));
        when(passwordHashService.hash(any())).thenReturn("newhash");

        traineeService.resetPassword(1L, 5L);

        verify(userDao).update(entity);
        assertThat(entity.getPasswordHash()).isEqualTo("newhash");
    }
}
