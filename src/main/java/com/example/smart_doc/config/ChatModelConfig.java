package com.example.smart_doc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

@Configuration
public class ChatModelConfig {

    @Bean
    public ChatModel chatModel(Environment environment) {

        String apiKey = environment.getProperty("OPENAI_API_KEY");

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .build();
    }
}