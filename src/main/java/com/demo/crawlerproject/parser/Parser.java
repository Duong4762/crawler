package com.demo.crawlerproject.parser;

import com.demo.crawlerproject.config.SelectorConfig;
import com.demo.crawlerproject.service.LlmService;
import com.demo.crawlerproject.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class Parser {

    @Autowired
    private RedisService redisService;

    @Autowired
    private LlmService llmService;

    public ParseData parse(String html, String url) throws Exception {
        try {
            Document doc = Jsoup.parse(html);

            if (looksLikeArticle(doc)) {
                SelectorConfig config = redisService.getSelectorConfig("vnexpress.net");

                if (config == null) {
                    synchronized (this) {
                        config = redisService.getSelectorConfig("vnexpress.net");
                        if (config == null) {
                            config = llmService.generateSelectorConfig(cleanHtmlForLlm(doc));
                            redisService.saveSelectorConfig("vnexpress.net", config);
                        }
                    }
                }

                ParseData article = new ParseData();
                Element titleElement = doc.selectFirst(config.getTitle());
                if (titleElement != null) {
                    article.setTitle(titleElement.text());
                }

                Element timeElement = doc.selectFirst(config.getPublishTime());
                if (timeElement != null) {
                    article.setPublishTime(timeElement.text());
                }

                Elements paragraphs = doc.select(config.getContent());
                StringBuilder contentBuilder = new StringBuilder();
                for (Element p : paragraphs) {
                    contentBuilder.append(p.text()).append("\n");
                }
                article.setContent(contentBuilder.toString().trim());
                article.setUrl(url);
                log.info("Parsed article: " + article);
                if (article.getTitle() == null || article.getPublishTime() == null || article.getContent() == null) {
                    throw new Exception("Article information is empty");
                }
                return article;
            } else {
                throw new Exception("This url has no article: " + url);
            }
        } catch (Exception e) {
            log.error("Error occur when parsing article");
            throw e;
        }
    }

    private String cleanHtmlForLlm(Document doc) {
        Element body = doc.body();
        body.select("script, style, noscript, iframe, header, footer, nav, aside").remove();
        return body.html();
    }

    public boolean looksLikeArticle(Document doc) {
        String ogType = Optional.ofNullable(doc.selectFirst("meta[property=og:type]"))
                .map(e -> e.attr("content")).orElse("").toLowerCase();

        return "article".equals(ogType) || (ogType.isEmpty());
    }
}
