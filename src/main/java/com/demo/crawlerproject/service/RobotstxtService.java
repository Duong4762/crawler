package com.demo.crawlerproject.service;

import com.demo.crawlerproject.config.CrawlerConfig;
import com.demo.crawlerproject.fetcher.Fetcher;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.BaseRobotsParser;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RobotstxtService {
    private static final String REDIS_KEY_PREFIX = "robots:";

    @Autowired
    private RedisService redisService;
    @Autowired
    private Fetcher fetcher;
    private final BaseRobotsParser parser = new SimpleRobotRulesParser();

    private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();

    private Object getHostLock(String host) {
        Object newLock = new Object();
        Object existing = hostLocks.putIfAbsent(host, newLock);
        return existing != null ? existing : newLock;
    }

    public boolean allows(String urlStr, String userAgent) {
        try {
            URI uri = URI.create(urlStr);
            String host = uri.getHost().toLowerCase();

            Object hostLock = getHostLock(host);

            synchronized (hostLock) {
                BaseRobotRules rules = loadRulesFromRedis(host);
                if (rules == null) {
                    rules = fetchAndParseRobots(uri, userAgent);
                    saveRulesToRedis(host, rules);
                }
                boolean allowed = rules.isAllowed(uri.getPath());
                if (!allowed) {
                    log.info("Blocked by robots.txt: {} {}", uri.getHost(), uri.getPath());
                }
                return allowed;
            }
        } catch (Exception e) {
            log.error("Error processing robots.txt for URL: {}", urlStr, e);
            return true;
        }
    }

    private BaseRobotRules fetchAndParseRobots(URI uri, String userAgent) {
        try {
            log.info("Fetching robots.txt for URL: {}", uri);
            String robotsUrl = uri.getScheme() + "://" + uri.getHost() + "/robots.txt";

            String contentStr = fetcher.fetch(robotsUrl);

            byte[] content = contentStr.getBytes(StandardCharsets.UTF_8);
            String contentType = "text/plain";

            return parser.parseContent(robotsUrl, content, contentType, userAgent);

        } catch (Exception e) {
            log.warn("Failed to fetch/parse robots.txt for host: {}", uri.getHost(), e);
        }

        return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);
    }

    private void saveRulesToRedis(String host, BaseRobotRules rules) {
        try {
            byte[] data = serializeRules(rules);
            String base64 = java.util.Base64.getEncoder().encodeToString(data);
            redisService.saveRobotsTxt(REDIS_KEY_PREFIX + host, base64, CrawlerConfig.ttlRobotstxt);
            log.debug("Saved robots.txt to Redis for host: {}", host);
        } catch (Exception e) {
            log.warn("Failed to save robots.txt to Redis for host: {}", host, e);
        }
    }

    private BaseRobotRules loadRulesFromRedis(String host) {
        try {
            String base64 = redisService.getRobotsTxt(REDIS_KEY_PREFIX + host);
            if (base64 != null) {
                byte[] data = java.util.Base64.getDecoder().decode(base64);
                return deserializeRules(data);
            }
        } catch (Exception e) {
            log.warn("Failed to load robots.txt from Redis for host: {}", host, e);
        }
        return null;
    }

    private byte[] serializeRules(BaseRobotRules rules) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(rules);
            return bos.toByteArray();
        }
    }

    private BaseRobotRules deserializeRules(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (BaseRobotRules) ois.readObject();
        }
    }
}
