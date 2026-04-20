package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.projection.AssignmentTaskProjection;
import com.example.training_platform.entity.TaskEntity;
import org.seasar.doma.BatchDelete;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TaskDao {

    @Select
    Optional<TaskEntity> selectById(Long id);

    @Select
    long countByAssignmentId(Long assignmentId);

    @Select
    Integer sumEstimatedDaysByAssignmentId(Long assignmentId);

    @Select
    Optional<TaskEntity> selectByAssignmentAndTaskId(Long assignmentId, Long taskId);

    @Select
    List<AssignmentTaskProjection> listAssignmentTasks(Long assignmentId);

    @Insert
    int insert(TaskEntity entity);

    @Update
    int update(TaskEntity entity);

    @BatchDelete
    int[] batchDelete(List<TaskEntity> entities);
}
