package com.demo.crawlerproject.fetcher;

import com.demo.crawlerproject.config.CrawlerConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class Fetcher {
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
                throw new Exception("Fetch failed: HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new Exception("Empty body");
            }
            log.info("Fetched {} completely", url);
            return body.string();
        } catch (Exception e) {
            throw e;
        }
    }

}
