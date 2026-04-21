package com.example.training_platform.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.training_platform.entity.WeeklyPerformanceSummaryEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface WeeklyPerformanceSummaryDao {

    @Select
    Optional<WeeklyPerformanceSummaryEntity> selectByAssignmentAndWeekStart(Long assignmentId, LocalDate weekStart);

    @Select
    List<WeeklyPerformanceSummaryEntity> listByAssignment(Long assignmentId);

    @Select
    List<WeeklyPerformanceSummaryEntity> listByAssignmentAndDateRange(Long assignmentId, LocalDate fromDate, LocalDate toDate);

    @Insert
    int insert(WeeklyPerformanceSummaryEntity entity);

    @Update
    int update(WeeklyPerformanceSummaryEntity entity);
}
