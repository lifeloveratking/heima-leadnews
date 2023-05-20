package com.heima.schedule.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @Author: [seim]
 * @emil: [webseim@126.com]
 * @ClassName MyConfig
 * @date 2021/7/17 10:49
 * @Version 15
 */
@Configuration
@EnableTransactionManagement//开启事务自动管理(默认是有的,这里熟悉一下)
@MapperScan("com.heima.schedule.mapper")
public class MyConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
       MybatisPlusInterceptor interceptor=new MybatisPlusInterceptor();
       interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
       return interceptor;
    }
}
