package com.hm.badminton;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@MapperScan("com.hm.badminton.mapper")
@EnableScheduling
@EnableTransactionManagement
@SpringBootApplication
public class HmBadmintonApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmBadmintonApplication.class, args);
    }
}

