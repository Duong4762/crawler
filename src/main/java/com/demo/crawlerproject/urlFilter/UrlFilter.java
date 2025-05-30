package com.demo.crawlerproject.urlFilter;

import com.demo.crawlerproject.config.CrawlerConfig;
import com.demo.crawlerproject.service.RedisService;
import com.demo.crawlerproject.url.Url;
import com.demo.crawlerproject.url.UrlCanonicalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UrlFilter {
    @Autowired
    private RedisService redisService;

    private final Set<String> allowedHosts;

    public UrlFilter() {
        this.allowedHosts = CrawlerConfig.seedUrls.stream()
                .map(seedUrl -> {
                    try {
                        return URI.create(seedUrl).getHost();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<String> filter(Set<String> urls) {
        Set<String> filteredUrls = new HashSet<>();
        for (String url : urls) {
            if (isAllowedHost(url) && !redisService.isUrlCrawled(url) && !redisService.isUrlProcessing(url)) {
                filteredUrls.add(url);
            }
        }
        log.info("Filtered urls: {}", filteredUrls);
        return filteredUrls;
    }

    private boolean isAllowedHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && allowedHosts.contains(host);
        } catch (Exception e) {
            return false;
        }
    }
}
