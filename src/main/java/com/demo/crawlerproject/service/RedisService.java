package com.demo.crawlerproject.service;

import com.demo.crawlerproject.config.SelectorConfig;
import com.demo.crawlerproject.url.Url;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void addCrawledUrl(String url) {
        redisTemplate.opsForSet().add("crawled", url);
    }

    public boolean isUrlCrawled(String url) {
        Boolean isMember = redisTemplate.opsForSet().isMember("crawled", url);
        return Boolean.TRUE.equals(isMember);
    }

    public void addProcessingUrl(String url) {
        redisTemplate.opsForSet().add("processing", url);
    }

    public void removeProcessingUrl(String url) {
        redisTemplate.opsForSet().remove("processing", url);
    }

    public boolean isUrlProcessing(String url) {
        Boolean isMember = redisTemplate.opsForSet().isMember("processing", url);
        return Boolean.TRUE.equals(isMember);
    }

    public void addTaskUrl(Url url) {
        redisTemplate.opsForZSet().add("task-queue", url.getUrl(), url.getDepth());
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

    public void addRetryUrl(String url) {
        redisTemplate.opsForSet().add("retry", url);
    }

    //method relate to monitor
    public Long getNumberOfCrawledUrl(){
        Long size = redisTemplate.opsForSet().size("crawled");
        return size != null ? size : 0L;
    }

    public Long getNumberOfProcessingUrl(){
        Long size = redisTemplate.opsForSet().size("processing");
        return size != null ? size : 0L;
    }

    public Long getNumberOfTaskUrl(){
        Long size = redisTemplate.opsForZSet().size("task-queue");
        return size != null ? size : 0L;
    }

    //method relate to selector config
    public void saveSelectorConfig(String domain, SelectorConfig config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            redisTemplate.opsForValue().set("selector:" + domain, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize selector config for domain {}", domain, e);
        }
    }

    public SelectorConfig getSelectorConfig(String domain) {
        String json = redisTemplate.opsForValue().get("selector:" + domain);
        if (json == null || json.equals("null")) return null;

        try {
            return objectMapper.readValue(json, SelectorConfig.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize selector config for domain {}", domain, e);
            return null;
        }
    }

}

