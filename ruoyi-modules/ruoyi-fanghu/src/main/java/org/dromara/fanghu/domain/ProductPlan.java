package org.dromara.fanghu.domain;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;
@Data @TableName("fh_product_plan")
public class ProductPlan extends FanghuEntity { private String code; private String name; private String description; private BigDecimal price; private String currency; private Integer durationMonths; private Boolean enabled; }
