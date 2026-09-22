package com.coffeul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 스케줄 작업 7개(rules.py)는 모듈마다 @Scheduled로 두고, 활성화만 여기서 한 번 한다.
@EnableScheduling
@SpringBootApplication
public class CoffeulApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeulApplication.class, args);
    }
}
