package org.dromara.fanghu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.dromara.fanghu.domain.SupplierMetric;
import org.apache.ibatis.annotations.Insert;

public interface SupplierMetricMapper extends BaseMapper<SupplierMetric> {
    @Insert("""
        INSERT INTO fh_supplier_metric(supplier_id, metric_date, impressions)
        VALUES(#{supplierId}, CURRENT_DATE, #{count})
        ON DUPLICATE KEY UPDATE impressions = impressions + VALUES(impressions)
        """)
    int addImpressions(Long supplierId, int count);
}
