package com.example.smart_doc.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.DocumentChunk;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;

/** Turns text into embedding vectors via the {@link EmbeddingModel} bean. */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Generates the embedding vector for one piece of text. */
    public float[] generateEmbedding(String text) {

        Embedding embedding = embeddingModel.embed(text).content();

        return embedding.vector();
    }

    /** Generates and sets an embedding on every chunk in the list. */
    public void generateEmbeddings(List<DocumentChunk> chunks) {

        for (DocumentChunk chunk : chunks) {

            float[] vector = generateEmbedding(chunk.getText());

            chunk.setEmbedding(vector);
        }
    }
}