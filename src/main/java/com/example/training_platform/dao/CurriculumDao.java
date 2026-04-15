package com.example.training_platform.dao;

import com.example.training_platform.entity.CurriculumEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface CurriculumDao {

    @Select
    @Sql("select id, name, description, created_by, created_at, updated_at from curricula where id = /* id */0")
    CurriculumEntity selectById(Long id);
}
