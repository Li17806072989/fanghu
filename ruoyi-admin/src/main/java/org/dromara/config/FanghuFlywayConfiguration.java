package org.dromara.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Flyway wiring for RuoYi's dynamic datasource.
 * Spring Boot cannot infer a JDBC URL from DynamicRoutingDataSource, so its
 * regular Flyway auto-configuration is skipped unless a Flyway bean is supplied.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FanghuFlywayConfiguration {

    @Bean(name = "flyway")
    public Flyway flyway(DynamicRoutingDataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .table("ruoyi_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .load();
        // Run while the datasource bean is being initialized. This is earlier
        // than RuoYi's scheduled/application runners which query Fanghu tables.
        flyway.migrate();
        return flyway;
    }
}
