package com.example.smart_doc.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.RetrievedChunk;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

/** Embeds a question and searches Qdrant for the most relevant chunks (the "R" in RAG). */
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

    /** Searches every uploaded document for chunks relevant to the question. */
    public List<EmbeddingMatch<TextSegment>> search(String question) {
        return search(question, null);
    }

    /**
     * Same as {@link #search(String)}, but restricted to the given
     * document names if any are provided (null/empty means search everything).
     */
    public List<EmbeddingMatch<TextSegment>> search(
            String question,
            List<String> documentNames) {

        // 1. Generate embedding for the user's question
        var questionEmbedding =
                embeddingModel.embed(question).content();

        // 2. Create search request, adding a document filter only
        // if the user actually selected documents
        var requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(3)
                .minScore(0.0);

        if (documentNames != null && !documentNames.isEmpty()) {

            Filter documentFilter =
                    MetadataFilterBuilder.metadataKey("documentName")
                            .isIn(new HashSet<>(documentNames));

            requestBuilder.filter(documentFilter);
        }

        EmbeddingSearchRequest request = requestBuilder.build();

        // 3. Search Qdrant
        EmbeddingSearchResult<TextSegment> result =
                embeddingStore.search(request);

        // 4. Return the matching chunks
        return result.matches();
    }

    /** Same search as {@link #search(String)}, but returns JSON-friendly {@link RetrievedChunk}s. */
    public List<RetrievedChunk> searchWithDetails(String question) {

        List<EmbeddingMatch<TextSegment>> matches = search(question);

        List<RetrievedChunk> retrievedChunks = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : matches) {

            Metadata metadata = match.embedded().metadata();

            RetrievedChunk retrievedChunk = new RetrievedChunk();

            retrievedChunk.setText(match.embedded().text());
            retrievedChunk.setScore(match.score());
            retrievedChunk.setDocumentName(metadata.getString("documentName"));
            retrievedChunk.setSection(metadata.getString("section"));
            retrievedChunk.setPageNumber(metadata.getInteger("pageNumber"));
            retrievedChunk.setChunkIndex(metadata.getInteger("chunkIndex"));

            retrievedChunks.add(retrievedChunk);
        }

        return retrievedChunks;
    }
}
