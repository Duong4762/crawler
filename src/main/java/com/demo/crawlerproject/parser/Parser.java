package com.demo.crawlerproject.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Parser {

    public ParseData parse(String html, String url) {
        Document doc = Jsoup.parse(html);

        ParseData article = new ParseData();
        Element titleElement = doc.selectFirst("h1.title-detail");
        if (titleElement != null) {
            article.setTitle(titleElement.text());
        }

        Element timeElement = doc.selectFirst("span.date");
        if (timeElement != null) {
            article.setPublishTime(timeElement.text());
        }

        Elements paragraphs = doc.select("p.Normal");
        StringBuilder contentBuilder = new StringBuilder();
        for (Element p : paragraphs) {
            contentBuilder.append(p.text()).append("\n");
        }
        article.setContent(contentBuilder.toString().trim());
        article.setUrl(url);
        log.info("Parsed article: " + article);
        return article;
    }
}
