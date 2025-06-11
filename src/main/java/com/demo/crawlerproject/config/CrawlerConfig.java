package com.demo.crawlerproject.config;

import java.util.List;
import java.util.Set;

public class CrawlerConfig {
    public static final int maxDepth = 1;
    public static final Set<String> seedUrls = Set.of(
            "https://vnexpress.net"
//            "https://tienphong.vn"
    );
    public static final int politenessDelay = 1000;
    public static final int ttlRobotstxt = 43200;
    public static final String userAgent = "MyCrawler";
}

