package com.demo.crawlerproject.urlExtractor;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class UrlExtractor {

    public Set<String> extractUrls(String htmlContent) {
        Set<String> urls = new HashSet<>();
        Document doc = Jsoup.parse(htmlContent);
        Elements links = doc.select("a[href]");

        for (Element link : links) {
            String url = link.attr("abs:href");
            if (!url.isEmpty()) {
                urls.add(url);
            }
        }
        log.info("Extracted urls: {}", urls);
        return urls;
    }
}
