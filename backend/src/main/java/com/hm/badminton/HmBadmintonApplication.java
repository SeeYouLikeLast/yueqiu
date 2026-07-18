package com.hm.badminton;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * “约个球”后端启动入口。
 *
 * <p>三个关键注解分别开启 Mapper 扫描、定时任务和声明式事务。Spring Boot 会从本包开始
 * 向下扫描 Controller、Service、配置类等 Bean，因此新增业务类应放在
 * {@code com.hm.badminton} 子包中。</p>
 */
@MapperScan("com.hm.badminton.mapper")
@EnableScheduling
@EnableTransactionManagement
@SpringBootApplication
public class HmBadmintonApplication {

    public static void main(String[] args) {
        // 创建 Spring 容器、读取 application*.yml，并启动内嵌 Web 服务器。
        SpringApplication.run(HmBadmintonApplication.class, args);
    }
}

