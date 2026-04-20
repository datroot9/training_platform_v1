package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.projection.MaterialCopyProjection;
import com.example.training_platform.entity.LearningMaterialEntity;
import org.seasar.doma.BatchDelete;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface LearningMaterialDao {

    @Select
    List<LearningMaterialEntity> listByCurriculumId(Long curriculumId);

    @Select
    Optional<LearningMaterialEntity> selectByIdAndCurriculumId(Long materialId, Long curriculumId);

    @Select
    Optional<LearningMaterialEntity> selectById(Long id);

    @Select
    List<String> listStoragePathsByCurriculumId(Long curriculumId);

    @Select
    long countByStoragePath(String storagePath);

    @Select
    Integer maxSortOrderByCurriculumId(Long curriculumId);

    @Select
    List<MaterialCopyProjection> listForCopy(Long curriculumId);

    @Select
    Optional<LearningMaterialEntity> selectAccessibleByTraineeAndMaterialId(Long traineeId, Long materialId);

    @Insert
    int insert(LearningMaterialEntity entity);

    @Update
    int update(LearningMaterialEntity entity);

    @Delete
    int delete(LearningMaterialEntity entity);

    @BatchDelete
    int[] batchDelete(List<LearningMaterialEntity> entities);
}
