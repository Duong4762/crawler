package com.demo.crawlerproject.store;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "articles")
@Data
public class Article {
    @Id
    private String id;
    private String url;
    private String title;
    private String publishTime;
    private String content;
    private String author;
}
