package com.hmdp.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.annotation.DbType; // 引入数据库类型
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring 配置类
@Configuration
public class MybatisPlusConfig {

    /**
     * 新的分页插件：MybatisPlusInterceptor
     * 3.5.0 及以上版本推荐
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 如果您还有其他拦截器（如乐观锁），也通过 addInnerInterceptor 添加
        // interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}