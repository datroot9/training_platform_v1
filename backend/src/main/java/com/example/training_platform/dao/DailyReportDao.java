package com.example.training_platform.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.example.training_platform.entity.DailyReportEntity;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface DailyReportDao {

    @Select
    Optional<DailyReportEntity> selectByAssignmentAndReportDate(Long assignmentId, LocalDate reportDate);

    @Select
    List<DailyReportEntity> listByAssignmentAndDateRange(Long assignmentId, LocalDate fromDate, LocalDate toDate);

    @Select
    List<DailyReportEntity> listByTraineeWithFilters(Long traineeId, Long assignmentId, LocalDate fromDate, LocalDate toDate);

    @Insert
    int insert(DailyReportEntity entity);

    @Update
    int update(DailyReportEntity entity);
}
