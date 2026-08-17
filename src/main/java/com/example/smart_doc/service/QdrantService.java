package com.example.smart_doc.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentChunk;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

/** Stores and removes document chunk vectors in Qdrant. */
@Service
public class QdrantService {

    private final QdrantEmbeddingStore embeddingStore;

    public QdrantService(QdrantEmbeddingStore embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    /** Stores each chunk's embedding vector and metadata in Qdrant. */
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

    /** Removes every chunk/vector belonging to one document. */
    public void deleteByDocumentName(String documentName) {

        Filter documentFilter =
                MetadataFilterBuilder.metadataKey("documentName")
                        .isEqualTo(documentName);

        embeddingStore.removeAll(documentFilter);
    }
}