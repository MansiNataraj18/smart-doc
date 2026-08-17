package com.example.smart_doc.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentChunk;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

@Service
public class QdrantService {

    private final QdrantEmbeddingStore embeddingStore;

    public QdrantService(QdrantEmbeddingStore embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public void storeChunks(List<DocumentChunk> chunks) {

        for (DocumentChunk chunk : chunks) {

            Embedding embedding =
                    Embedding.from(chunk.getEmbedding());

            TextSegment textSegment =
                    TextSegment.from(chunk.getText());

            textSegment.metadata().put(
                    "documentName",
                    chunk.getDocumentName()
            );

            textSegment.metadata().put(
                    "pageNumber",
                    chunk.getPageNumber()
            );

            textSegment.metadata().put(
                    "chunkIndex",
                    chunk.getChunkIndex()
            );

            if (chunk.getSection() != null) {
                textSegment.metadata().put(
                        "section",
                        chunk.getSection()
                );
            }

            embeddingStore.add(
                    embedding,
                    textSegment
            );
        }
    }

    // Removes every chunk/vector belonging to one document, using the
    // SAME "documentName" metadata field every chunk is stored with
    // in storeChunks() above -- this is what RetrievalService already
    // filters on for document selection, so re-using it here means
    // deletion and search stay in sync automatically. Used by the
    // DELETE /documents endpoint so a removed document can never be
    // returned by retrieval again.
    public void deleteByDocumentName(String documentName) {

        Filter documentFilter =
                MetadataFilterBuilder.metadataKey("documentName")
                        .isEqualTo(documentName);

        embeddingStore.removeAll(documentFilter);
    }
}