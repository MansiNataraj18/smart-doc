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

    // Kept exactly as before, for anything that doesn't care about
    // document filtering (the /documents/search debug endpoint).
    // Internally this now just calls the new overload with "no
    // documents selected", which means "search everything" -- so
    // behavior here is unchanged.
    public List<EmbeddingMatch<TextSegment>> search(String question) {
        return search(question, null);
    }

    // Same as search(question), but optionally restricted to only
    // the given document names.
    //
    // - documentNames == null or empty -> search ALL documents,
    //   exactly like before this feature existed.
    // - documentNames has entries -> Qdrant itself only looks at
    //   chunks whose "documentName" payload field is one of these,
    //   using the SAME metadata field ChunkingService/QdrantService
    //   already store on every chunk. This is a filter applied
    //   during the vector search, not a manual filter afterward.
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

    // Used by the /documents/search debug endpoint. EmbeddingMatch's
    // accessor methods (score(), embedded()...) don't follow the
    // getX() naming Jackson expects, so returning it directly from
    // a controller serializes to "{}". This converts each match into
    // RetrievedChunk, which has normal getters/setters, so the JSON
    // actually shows the text/metadata/score.
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
