package com.example.smart_doc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/** Creates the {@link EmbeddingModel} bean used to turn text into vectors. */
@Configuration
public class EmbeddingConfig {

    /** Builds the OpenAI text-embedding-3-small embedding model bean. */
    @Bean
    public EmbeddingModel embeddingModel(Environment environment) {

        String apiKey = environment.getProperty("OPENAI_API_KEY");

        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-3-small")
                .build();
    }
}
