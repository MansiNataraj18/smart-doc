package com.example.smart_doc.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

@Service
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final QdrantEmbeddingStore embeddingStore;

    public RetrievalService(
            EmbeddingModel embeddingModel,
            QdrantEmbeddingStore embeddingStore) {

        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public List<EmbeddingMatch<TextSegment>> search(String question) {

        // 1. Generate embedding for the user's question
        var questionEmbedding =
                embeddingModel.embed(question).content();

        // 2. Create search request
        EmbeddingSearchRequest request =
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(3)
                        .minScore(0.0)
                        .build();

        // 3. Search Qdrant
        EmbeddingSearchResult<TextSegment> result =
                embeddingStore.search(request);

        // 4. Return the matching chunks
        return result.matches();
    }
}
