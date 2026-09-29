package org.dromara.fanghu.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("org.dromara.fanghu.mapper")
public class MybatisConfig {
}
