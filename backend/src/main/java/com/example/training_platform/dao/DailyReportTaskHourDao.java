package com.example.training_platform.dao;

import java.util.List;

import com.example.training_platform.entity.DailyReportTaskHourEntity;
import org.seasar.doma.BatchDelete;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface DailyReportTaskHourDao {

    @Select
    List<DailyReportTaskHourEntity> listByDailyReportId(Long dailyReportId);

    @Insert
    int insert(DailyReportTaskHourEntity entity);

    @BatchDelete
    int[] batchDelete(List<DailyReportTaskHourEntity> entities);
}
