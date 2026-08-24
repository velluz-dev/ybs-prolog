package com.ybs.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class YbsDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(YbsDemoApplication.class, args);
    }
}
