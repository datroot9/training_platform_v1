package com.example.training_platform.dao;

import java.util.List;
import java.util.Optional;

import com.example.training_platform.dao.projection.TaskTemplateCopyProjection;
import com.example.training_platform.entity.TaskTemplateEntity;
import org.seasar.doma.BatchDelete;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TaskTemplateDao {

    @Select
    List<TaskTemplateEntity> listByCurriculumId(Long curriculumId);

    @Select
    Optional<TaskTemplateEntity> selectByIdAndCurriculumId(Long templateId, Long curriculumId);

    @Select
    long countByIdAndCurriculumId(Long templateId, Long curriculumId);

    @Select
    Integer maxSortOrderByCurriculumId(Long curriculumId);

    @Select
    List<TaskTemplateCopyProjection> listForCopy(Long curriculumId);

    @Select
    long countByLearningMaterialAndCurriculum(Long materialId, Long curriculumId);

    @Insert
    int insert(TaskTemplateEntity entity);

    @Update
    int update(TaskTemplateEntity entity);

    @Delete
    int delete(TaskTemplateEntity entity);

    @BatchDelete
    int[] batchDelete(List<TaskTemplateEntity> entities);
}
