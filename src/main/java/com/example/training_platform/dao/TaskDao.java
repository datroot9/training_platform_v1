package com.example.training_platform.dao;

import com.example.training_platform.entity.TaskEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TaskDao {

    @Select
    @Sql("select id, assignment_id, task_template_id, title, description, status, started_at, completed_at, created_at, updated_at from tasks where id = /* id */0")
    TaskEntity selectById(Long id);
}
