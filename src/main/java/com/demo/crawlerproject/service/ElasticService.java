package com.demo.crawlerproject.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.json.JsonData;
import com.demo.crawlerproject.store.Article;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElasticService {
    ObjectMapper mapper = new ObjectMapper();
    private static final String INDEX_NAME = "articles";
    @Autowired
    private ElasticsearchClient es;

    @PostConstruct
    public void createIndex() {
        try {
            boolean exists = es.indices().exists(r -> r.index(INDEX_NAME)).value();
            if (!exists) {
                es.indices().create(c -> c
                        .index(INDEX_NAME)
                        .mappings(m -> m
                                .properties("title", p -> p.text(t -> t.analyzer("standard")))
                                .properties("content", p -> p.text(t -> t.analyzer("standard")))
                                .properties("url", p -> p.keyword(k -> k))
                                .properties("author", p -> p.keyword(k -> k))
                                .properties("publishTime", p -> p.keyword(k -> k))
                                .properties("vector", p -> p.denseVector(dv -> dv.dims(384)))
                        )
                );
                System.out.println("✅ Created index 'articles'");
            } else {
                System.out.println("ℹ️ Index 'articles' already exists");
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ Error creating index", e);
        }
    }

    public void index(Article article) {
        try {
            float[] vector = generateEmbedding(article.getTitle() + " " + article.getContent());
            Map<String, Object> doc = new HashMap<>();
            if (article.getTitle() != null) doc.put("title", article.getTitle());
            if (article.getContent() != null) doc.put("content", article.getContent());
            if (article.getUrl() != null) doc.put("url", article.getUrl());
            if (article.getAuthor() != null) doc.put("author", article.getAuthor());
            if (article.getPublishTime() != null) doc.put("publishTime", article.getPublishTime());
            doc.put("vector", vector);
            es.index(i -> i
                    .index("articles")
                    .id(article.getId())
                    .document(doc)
            );
            System.out.println("✅ Indexed article: " + article.getId());
        } catch (Exception e) {
            log.error("Error occur when indexing article", e);
        }
    }

    public List<Article> fullTextSearch(String keyword) {
        try {
            SearchResponse<Map> response = es.search(s -> s
                            .index("articles")
                            .query(q -> q
                                    .multiMatch(m -> m
                                            .query(keyword)
                                            .fields("title", "content")
                                            .operator(Operator.And)
                                    )
                            ),
                    Map.class
            );

            return response.hits().hits().stream().map(hit -> {
                Map<String, Object> src = hit.source();
                Article article = new Article();
                article.setId(hit.id());
                article.setTitle((String) src.get("title"));
                article.setContent((String) src.get("content"));
                article.setUrl((String) src.get("url"));
                article.setAuthor((String) src.get("author"));
                article.setPublishTime((String) src.get("publishTime"));
                return article;
            }).collect(Collectors.toList());

        } catch (IOException e) {
            throw new RuntimeException("❌ Error searching articles", e);
        }
    }

    public List<Article> semanticSearch(String queryText) {
        log.info("==============================================================");
        try {
            float[] queryVector = generateEmbedding(queryText);

            String queryVectorJson = mapper.writeValueAsString(queryVector);

            if (queryVectorJson == null || queryVectorJson.isEmpty()) {
                throw new IllegalArgumentException("Embedding vector is null or empty");
            }

            SearchResponse<Map> response = es.search(s -> s
                            .index("articles")
                            .query(q -> q
                                    .scriptScore(ss -> ss
                                            .query(innerQ -> innerQ.matchAll(m -> m)) // hoặc filter khác nếu cần
                                            .script(script -> script
                                                    .inline(i -> i
                                                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                                                            .params("query_vector", JsonData.fromJson(queryVectorJson))
                                                    )
                                            )
                                    )
                            ),
                    Map.class
            );

            return response.hits().hits().stream().map(hit -> {
                Map<String, Object> src = hit.source();
                Article article = new Article();
                article.setId(hit.id());
                article.setTitle((String) src.get("title"));
                article.setContent((String) src.get("content"));
                article.setUrl((String) src.get("url"));
                article.setAuthor((String) src.get("author"));
                article.setPublishTime((String) src.get("publishTime"));
                return article;
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("❌ Error performing semantic search", e);
        }
    }

    public static float[] generateEmbedding(String text) throws IOException {
        OkHttpClient client = new OkHttpClient();

        String json = "{\"sentences\": [\"" + text.replace("\"", "\\\"") + "\"]}";

        Request request = new Request.Builder()
                .url("http://localhost:8000/embed")
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Embedding server error: " + response);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode array = mapper.readTree(response.body().string()).get(0);

            float[] vector = new float[array.size()];
            for (int i = 0; i < array.size(); i++) {
                vector[i] = array.get(i).floatValue();
            }
            return vector;
        }
    }
}
