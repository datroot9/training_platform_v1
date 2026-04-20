package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.projection.CurriculumSourceProjection;
import com.example.training_platform.entity.CurriculumEntity;
import org.seasar.doma.Delete;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface CurriculumDao {

    @Select
    Optional<CurriculumEntity> selectById(Long id);

    @Select
    Optional<CurriculumEntity> selectByIdAndCreator(Long id, Long creatorId);

    @Select
    long countByCreatorWithFilter(Long creatorId, String keyword, String status);

    @Select
    List<CurriculumEntity> listByCreatorWithFilter(
            Long creatorId,
            String keyword,
            String status,
            String sortBy,
            String sortDir,
            int limit,
            long offset
    );

    @Select
    Optional<CurriculumSourceProjection> selectSourceForFork(Long sourceCurriculumId, Long creatorId);

    @Select
    long countByGroupAndVersion(Long groupId, String versionLabel);

    @Select
    long countByIdCreatorDraft(Long curriculumId, Long creatorId);

    @Select
    long countByIdCreatorDraftOrPublished(Long curriculumId, Long creatorId);

    @Insert
    int insert(CurriculumEntity entity);

    @Update
    int update(CurriculumEntity entity);

    @Delete
    int delete(CurriculumEntity entity);
}
