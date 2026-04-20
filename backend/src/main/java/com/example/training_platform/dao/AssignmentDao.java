package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.projection.AssignmentProjection;
import com.example.training_platform.entity.TraineeCurriculumAssignmentEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface AssignmentDao {

    @Select
    long countActiveByTrainee(Long traineeId);

    @Select
    Optional<TraineeCurriculumAssignmentEntity> selectActiveByTrainee(Long traineeId);

    @Select
    Optional<AssignmentProjection> selectAssignmentProjectionByIdAndTrainee(Long assignmentId, Long traineeId);

    @Select
    Optional<AssignmentProjection> selectActiveAssignmentProjectionByTrainee(Long traineeId);

    @Select
    long countByIdAndTrainee(Long assignmentId, Long traineeId);

    @Select
    long countByCurriculum(Long curriculumId);

    @Insert
    int insert(TraineeCurriculumAssignmentEntity entity);

    @Update
    int update(TraineeCurriculumAssignmentEntity entity);
}
