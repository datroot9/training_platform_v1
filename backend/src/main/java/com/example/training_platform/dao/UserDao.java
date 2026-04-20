package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.projection.AuthUserProjection;
import com.example.training_platform.dao.projection.TraineeListProjection;
import com.example.training_platform.dao.projection.UserBasicProjection;
import com.example.training_platform.entity.UserEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface UserDao {

    @Select
    Optional<UserEntity> selectById(Long id);

    @Select
    Optional<AuthUserProjection> selectAuthUserByEmail(String email);

    @Select
    Optional<AuthUserProjection> selectAuthUserById(Long userId);

    @Select
    Optional<UserEntity> selectActiveUserById(Long userId);

    @Select
    long countTrainees(Long mentorId, String keyword, Boolean active);

    @Select
    List<TraineeListProjection> listTrainees(Long mentorId, String keyword, Boolean active, String sortBy, String sortDir, int limit, long offset);

    @Select
    Optional<UserBasicProjection> findTraineeBasic(Long mentorId, Long traineeId);

    @Select
    long countTraineeByMentor(Long mentorId, Long traineeId);

    @Insert
    int insert(UserEntity entity);

    @Update
    int update(UserEntity entity);
}
