package com.fanghu.platform.config;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*; import com.baomidou.mybatisplus.extension.plugins.*; import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor; import com.baomidou.mybatisplus.annotation.DbType;
@Configuration @MapperScan("com.fanghu.platform.mapper") public class MybatisConfig { @Bean public MybatisPlusInterceptor pagination(){MybatisPlusInterceptor i=new MybatisPlusInterceptor();i.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));return i;} }
