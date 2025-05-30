package com.demo.crawlerproject.crawler;

import com.demo.crawlerproject.config.CrawlerConfig;
import com.demo.crawlerproject.fetcher.Fetcher;
import com.demo.crawlerproject.frontier.Frontier;
import com.demo.crawlerproject.parser.ParseData;
import com.demo.crawlerproject.parser.Parser;
import com.demo.crawlerproject.service.RedisService;
import com.demo.crawlerproject.service.RobotstxtService;
import com.demo.crawlerproject.store.StoreService;
import com.demo.crawlerproject.url.Url;
import com.demo.crawlerproject.url.UrlCanonicalizer;
import com.demo.crawlerproject.urlExtractor.UrlExtractor;
import com.demo.crawlerproject.urlFilter.UrlFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope("prototype")
@Slf4j
public class Crawler implements Runnable {
    private volatile boolean running = true;
    @Autowired
    private RobotstxtService robotstxtService;
    @Autowired
    private Frontier frontier;
    @Autowired
    private Fetcher fetcher;
    @Autowired
    private Parser parser;
    @Autowired
    private UrlExtractor urlExtractor;
    @Autowired
    private UrlFilter urlFilter;
    @Autowired
    private StoreService storeService;
    @Autowired
    private RedisService redisService;

    @Override
    public void run() {
        while (running) {
            Url url = frontier.getNextUrl();
            if (url == null) {
                try {
                    log.info("Waiting for frontier...");
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    log.error("Error occurred: ", e);
                }
            } else {
                frontier.addProcessingUrl(url.getUrl());
                try {
                    if(!robotstxtService.allows(url.getUrl(), CrawlerConfig.userAgent)){
                        log.info("Skipped url because robotstxt is not allowed");
                        continue;
                    }
                    String pageContent = fetcher.fetch(url.getUrl());
                    if (url.getDepth() < CrawlerConfig.maxDepth){
                        Set<String> urlExtracted = urlExtractor.extractUrls(pageContent);
                        Set<String> normalizedUrls = urlExtracted.stream()
                                .map(u -> UrlCanonicalizer.normalizeUrl(u, url.getUrl()))
                                .collect(Collectors.toSet());
                        Set<String> newUrls = urlFilter.filter(normalizedUrls);
                        for (String newUrl : newUrls) {
                            frontier.addTaskUrl(new Url(newUrl, url.getDepth() + 1));
                        }
                    }
                    ParseData article = parser.parse(pageContent, url.getUrl());
                    storeService.saveArticle(article);
                    redisService.addCrawledUrl(url.getUrl());
                } catch (Exception e){
                    log.error("Error occurred: ", e);
                } finally {
                    redisService.removeProcessingUrl(url.getUrl());
                }
            }
        }
    }

    public void stop(){
        log.info("Stopping Crawler");
        running = false;
    }
}
