package com.demo.crawlerproject.store;

import com.demo.crawlerproject.parser.ParseData;
import com.demo.crawlerproject.service.ElasticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StoreService {
    @Autowired
    private ElasticService elasticService;

    @Autowired
    private ArticleRepository articleRepository;

    public void saveArticle(ParseData data) {
        Article article = new Article();
        article.setUrl(data.getUrl());
        article.setTitle(data.getTitle());
        article.setAuthor(data.getAuthor());
        article.setContent(data.getContent());
        article.setPublishTime(data.getPublishTime());
        articleRepository.save(article);
        log.info("Saved article from url: {}", data.getUrl());
        log.info("Index to es article from url: {}", data.getUrl());
        elasticService.index(article);
    }
}
