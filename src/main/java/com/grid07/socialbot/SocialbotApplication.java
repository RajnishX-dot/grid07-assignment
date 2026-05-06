package com.grid07.socialbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SocialbotApplication {
    public static void main(String[] args) {
        SpringApplication.run(SocialbotApplication.class, args);
    }
}
