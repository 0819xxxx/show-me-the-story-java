package com.showmethestory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ShowMeTheStoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShowMeTheStoryApplication.class, args);
    }
}
