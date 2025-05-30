package com.demo.crawlerproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrawlerProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrawlerProjectApplication.class, args);
    }

}
