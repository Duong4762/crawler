package com.demo.crawlerproject.service;

import com.demo.crawlerproject.url.Url;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@Slf4j
public class RedisService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    // Getter cho Micrometer gauge
    @Getter
    private volatile double crawledSize = 0;
    @Getter
    private volatile double processingSize = 0;
    @Getter
    private volatile double taskQueueSize = 0;

    @PostConstruct
    public void init() {
        Gauge.builder("crawler_crawled_set_size", this, RedisService::getCrawledSize)
                .description("Size of crawled URLs set in Redis")
                .register(meterRegistry);

        Gauge.builder("crawler_processing_set_size", this, RedisService::getProcessingSize)
                .description("Size of processing URLs set in Redis")
                .register(meterRegistry);

        Gauge.builder("crawler_task_queue_size", this, RedisService::getTaskQueueSize)
                .description("Size of task queue in Redis")
                .register(meterRegistry);
    }

    public void addCrawledUrl(String url) {
        redisTemplate.opsForSet().add("crawled", url);
        crawledSize = redisTemplate.opsForSet().size("crawled");
    }

    public boolean isUrlCrawled(String url) {
        Boolean isMember = redisTemplate.opsForSet().isMember("crawled", url);
        return Boolean.TRUE.equals(isMember);
    }

    public void addProcessingUrl(String url) {
        redisTemplate.opsForSet().add("processing", url);
        processingSize = redisTemplate.opsForSet().size("processing");
    }

    public void removeProcessingUrl(String url) {
        redisTemplate.opsForSet().remove("processing", url);
        processingSize = redisTemplate.opsForSet().size("processing");
    }

    public boolean isUrlProcessing(String url) {
        Boolean isMember = redisTemplate.opsForSet().isMember("processing", url);
        return Boolean.TRUE.equals(isMember);
    }

    public void addTaskUrl(Url url) {
        redisTemplate.opsForZSet().add("task-queue", url.getUrl(), url.getDepth());
        taskQueueSize = redisTemplate.opsForZSet().size("task-queue");
    }

    @Synchronized
    public Url popTaskUrl() {
        Set<ZSetOperations.TypedTuple<String>> set = redisTemplate.opsForZSet()
                .rangeWithScores("task-queue", 0, 0);
        if (set != null && !set.isEmpty()) {
            ZSetOperations.TypedTuple<String> tuple = set.iterator().next();
            String url = tuple.getValue();
            Double score = tuple.getScore();
            redisTemplate.opsForZSet().remove("task-queue", url);
            taskQueueSize = redisTemplate.opsForZSet().size("task-queue");
            return new Url(url, score);
        }
        return null;
    }

    public void saveRobotsTxt(String key, String base64Data, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, base64Data);
        redisTemplate.expire(key, java.time.Duration.ofSeconds(ttlSeconds));
    }

    public String getRobotsTxt(String key) {
        return redisTemplate.opsForValue().get(key);
    }

}

