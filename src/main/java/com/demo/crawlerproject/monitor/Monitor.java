package com.demo.crawlerproject.monitor;

import com.demo.crawlerproject.service.RedisService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Monitor {
    @Autowired
    private RedisService redisService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Getter
    private Counter crawled;

    @Getter
    private double processing;

    @Getter
    private double task;

    @PostConstruct
    public void init() {
        processing = redisService.getNumberOfProcessingUrl();
        task = redisService.getNumberOfTaskUrl();

        crawled = Counter.builder("crawler.crawled")
                .register(meterRegistry);

        long savedCrawledCount = redisService.getNumberOfCrawledUrl();
        if (savedCrawledCount > 0) {
            crawled.increment(savedCrawledCount);
        }

        Gauge.builder("crawler.processing", this, Monitor::getProcessing)
                .register(meterRegistry);

        Gauge.builder("crawler.task", this, Monitor::getTask)
                .register(meterRegistry);
    }

    // Tăng số bài đã crawl
    public void incrementCrawled() {
        crawled.increment();
    }

    // Tăng processing
    public void incrementProcessing() {
        processing += 1;
    }

    // Giảm processing
    public void decrementProcessing() {
        processing -= 1;
    }

    // Tăng task
    public void incrementTask() {
        task += 1;
    }

    // Giảm task
    public void decrementTask() {
        task -= 1;
    }

    public void recordCrawlDuration(Runnable task) {
        Timer timer = Timer.builder("crawler.duration")
                .description("Time taken to crawl one URL")
                .register(meterRegistry);
        timer.record(task);
    }
}
