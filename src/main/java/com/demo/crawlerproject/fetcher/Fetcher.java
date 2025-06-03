package com.demo.crawlerproject.fetcher;

import com.demo.crawlerproject.config.CrawlerConfig;
import com.demo.crawlerproject.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class Fetcher {

    @Autowired
    private RedisService redisService;

    private final OkHttpClient client;
    private long lastFetchTime = 0L;
    private final Object mutex = new Object();

    public Fetcher() {
        this.client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public String fetch(String url) throws Exception {
        log.info("Fetching " + url);
        synchronized (mutex) {
            long now = System.currentTimeMillis();
            long timeSinceLastFetch = now - lastFetchTime;
            if (timeSinceLastFetch < CrawlerConfig.politenessDelay) {
                Thread.sleep(CrawlerConfig.politenessDelay - timeSinceLastFetch);
            }
            lastFetchTime = System.currentTimeMillis();
        }

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", CrawlerConfig.userAgent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                int code = response.code();
                if (code >= 500 && code < 600) {
                    log.warn("Server error {}, add url to retry queue", code);
                    redisService.addRetryUrl(url);
                    throw new Exception("Server error " + code);
                } else {
                    throw new Exception("Fetch failed: HTTP " + code);
                }
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new Exception("Empty body");
            }
            log.info("Fetched {} completely", url);
            return body.string();
        } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
            log.warn("Temporary network error ({}), add url to retry queue", e.getClass().getSimpleName());
            redisService.addRetryUrl(url);
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

}
