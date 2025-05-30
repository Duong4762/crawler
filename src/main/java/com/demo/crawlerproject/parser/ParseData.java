package com.demo.crawlerproject.parser;

import lombok.Data;

@Data
public class ParseData {
    private String url;
    private String title;
    private String publishTime;
    private String content;
    private String author;
}
