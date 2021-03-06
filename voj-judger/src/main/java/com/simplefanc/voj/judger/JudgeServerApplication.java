package com.simplefanc.voj.judger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @Author: chenfan
 * @Date: 2021/10/29 22:12
 * @Description: 判题机服务系统启动类
 */
@EnableDiscoveryClient // 开启服务注册发现功能
@SpringBootApplication
@EnableAsync(proxyTargetClass = true) // 开启异步注解
@EnableTransactionManagement
public class JudgeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JudgeServerApplication.class, args);
    }

}