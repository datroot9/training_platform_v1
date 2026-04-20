package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.entity.RefreshTokenEntity;
import org.seasar.doma.BatchUpdate;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface RefreshTokenDao {

    @Select
    long countValidToken(Long userId, String tokenHash);

    @Select
    Optional<RefreshTokenEntity> selectActiveToken(Long userId, String tokenHash);

    @Select
    List<RefreshTokenEntity> selectAllActiveTokensByUser(Long userId);

    @Insert
    int insert(RefreshTokenEntity entity);

    @Update
    int update(RefreshTokenEntity entity);

    @BatchUpdate
    int[] batchUpdate(List<RefreshTokenEntity> entities);
}
