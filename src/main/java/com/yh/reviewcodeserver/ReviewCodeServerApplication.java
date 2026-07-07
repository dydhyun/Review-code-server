package com.yh.reviewcodeserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ReviewCodeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewCodeServerApplication.class, args);
    }

}
