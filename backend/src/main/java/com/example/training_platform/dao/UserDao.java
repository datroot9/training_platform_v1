package com.example.training_platform.dao;

import com.example.training_platform.entity.UserEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.Sql;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface UserDao {

    @Select
    @Sql("select id, email, full_name, role, password_hash, must_change_password, password_updated_at, mentor_id, is_active, created_at, updated_at from users where id = /* id */0")
    UserEntity selectById(Long id);
}
