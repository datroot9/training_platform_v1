package com.example.training_platform.dao;

import java.util.List;

import com.example.training_platform.entity.DailyReportResourceEntity;
import org.seasar.doma.BatchDelete;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface DailyReportResourceDao {

    @Select
    List<DailyReportResourceEntity> listByDailyReportId(Long dailyReportId);

    @Insert
    int insert(DailyReportResourceEntity entity);

    @BatchDelete
    int[] batchDelete(List<DailyReportResourceEntity> entities);
}
