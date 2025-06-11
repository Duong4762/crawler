package com.demo.crawlerproject.controller;

import com.demo.crawlerproject.service.ElasticService;
import com.demo.crawlerproject.store.Article;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ElasticService elasticService;

    @CrossOrigin(origins = "*")
    @GetMapping("/full-text-search")
    public List<Article> fullTextSearch(@RequestParam String keyword) {
        List<Article> articles = elasticService.fullTextSearch(keyword);
        return articles;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/semantic-search")
    public List<Article> semanticSearch(@RequestParam String keyword) {
        List<Article> articles = elasticService.semanticSearch(keyword);
        return articles;
    }
}
