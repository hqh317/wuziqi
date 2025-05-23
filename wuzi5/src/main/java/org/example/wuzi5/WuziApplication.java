package org.example.wuzi5;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@MapperScan("org.example.wuzi5.demos.mapper")
public class WuziApplication {

    public static void main(String[] args) {
        SpringApplication.run(WuziApplication.class, args);
    }

}
