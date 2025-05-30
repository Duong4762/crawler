package com.demo.crawlerproject.crawler;

import com.demo.crawlerproject.config.CrawlerConfig;
import com.demo.crawlerproject.service.RedisService;
import com.demo.crawlerproject.url.Url;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class CrawlerManager {
    @Autowired
    private RedisService redisService;
    @Autowired
    private ApplicationContext context;

    private final List<Crawler> crawlers = new ArrayList<>();
    private static final int NUM_THREADS = 3;

    @PostConstruct
    public void startCrawlers() {
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            Crawler crawler = context.getBean(Crawler.class);
            crawlers.add(crawler);
            executor.submit(crawler);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            crawlers.forEach(Crawler::stop);
            executor.shutdown();
        }));
    }

}
