package com.demo.crawlerproject.parser;

import com.demo.crawlerproject.config.SelectorConfig;
import com.demo.crawlerproject.monitor.Monitor;
import com.demo.crawlerproject.service.LlmService;
import com.demo.crawlerproject.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class Parser {

    private final Map<String, Object> lockMap = new ConcurrentHashMap<>();

    @Autowired
    private RedisService redisService;

    @Autowired
    private LlmService llmService;

    @Autowired
    private Monitor monitor;

    public ParseData parse(String html, String url) throws Exception {
        try {
            Document doc = Jsoup.parse(html);
            String domain = extractDomain(url);

            if (looksLikeArticle(doc)) {
                SelectorConfig config = redisService.getSelectorConfig(domain);

                if (config == null) {
                    synchronized (getLock(domain)) {
                        config = redisService.getSelectorConfig(domain);
                        if (config == null) {
                            config = llmService.generateSelectorConfig(cleanHtmlForLlm(doc));
                            redisService.saveSelectorConfig(domain, config);
                        }
                    }
                }

                ParseData article = parseDoc(doc, url, config);

                log.info("Parsed article: " + article);
                if (article.getTitle() == null || article.getPublishTime() == null || article.getContent() == null) {
                    log.info("Selector config has changed, need to re-call LLM");

                    synchronized (getLock(domain)) {
                        config = redisService.getSelectorConfig(domain);

                        ParseData testArticle = parseDoc(doc, url, config);

                        if (testArticle.getTitle() == null || testArticle.getPublishTime() == null || testArticle.getContent() == null) {
                            config = llmService.generateSelectorConfig(cleanHtmlForLlm(doc));
                            redisService.saveSelectorConfig(domain, config);
                            article = parseDoc(doc, url, config);
                        } else {
                            article = testArticle;
                        }
                    }
                }
                return article;
            } else {
                monitor.incrementPassed();
                throw new Exception("This url has no article: " + url);
            }
        } catch (Exception e) {
            log.error("Error occur when parsing article");
            throw e;
        }
    }

    private String cleanHtmlForLlm(Document doc) {
        Element body = doc.body();
        body.select("script, style, noscript, iframe, footer, nav, aside, img").remove();
        return body.html();
    }

    public boolean looksLikeArticle(Document doc) {
        String ogType = Optional.ofNullable(doc.selectFirst("meta[property=og:type]"))
                .map(e -> e.attr("content")).orElse("").toLowerCase();

        return "article".equals(ogType) || (ogType.isEmpty());
    }

    private ParseData parseDoc(Document doc, String url, SelectorConfig config) {
        ParseData article = new ParseData();

        // Kiểm tra title selector
        if (config.getTitle() != null && !config.getTitle().isEmpty()) {
            Element titleElement = doc.selectFirst(config.getTitle());
            if (titleElement != null) {
                article.setTitle(titleElement.text());
            }
        }

        // Kiểm tra time selector
        if (config.getPublishTime() != null && !config.getPublishTime().isEmpty()) {
            Element timeElement = doc.selectFirst(config.getPublishTime());
            if (timeElement != null) {
                article.setPublishTime(timeElement.text());
            }
        }

        // Kiểm tra content selector
        if (config.getContent() != null && !config.getContent().isEmpty()) {
            Elements paragraphs = doc.select(config.getContent());
            StringBuilder contentBuilder = new StringBuilder();
            for (Element p : paragraphs) {
                contentBuilder.append(p.text()).append("\n");
            }
            article.setContent(contentBuilder.toString().trim());
        }

        article.setUrl(url);
        return article;
    }


    private Object getLock(String domain) {
        return lockMap.computeIfAbsent(domain, k -> new Object());
    }

    private String extractDomain(String url) throws Exception {
        URI uri = new URI(url);
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
