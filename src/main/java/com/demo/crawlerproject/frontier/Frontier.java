package com.demo.crawlerproject.frontier;

import com.demo.crawlerproject.config.CrawlerConfig;
import com.demo.crawlerproject.service.RedisService;
import com.demo.crawlerproject.url.Url;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;


@Service
@Slf4j
public class Frontier {
    @Autowired
    private RedisService redisService;

    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void enqueueSeedUrls() {
        Set<String> seedUrls = CrawlerConfig.seedUrls;
        for (String url : seedUrls) {
            Url seed = new Url(url, 0);
            redisService.addTaskUrl(seed);
        }
        log.info("Add seed urls");
    }

    public Url getNextUrl(){
        return  redisService.popTaskUrl();
    }

    public void addTaskUrl(Url url){
        redisService.addTaskUrl(url);
        log.info("Add task url: {}", url);
    }

    public void addProcessingUrl(String url){
        redisService.addProcessingUrl(url);
        log.info("Add processing url: {}", url);
    }
}
