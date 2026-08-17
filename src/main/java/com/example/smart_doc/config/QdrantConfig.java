package com.example.smart_doc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

/** Connects to the local Qdrant vector database. */
@Configuration
public class QdrantConfig {

    /** Builds the Qdrant embedding store bean. */
    @Bean
    public QdrantEmbeddingStore qdrantEmbeddingStore() {

        return QdrantEmbeddingStore.builder()
                .host("localhost")
                .port(6334)
                .collectionName("smartdoc_documents")
                .payloadTextKey("text")
                .useTls(false)
                .build();
    }
}
