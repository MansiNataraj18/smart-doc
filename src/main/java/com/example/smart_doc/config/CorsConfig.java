package com.example.smart_doc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// This exists ONLY to fix CORS -- it does not touch any RAG logic
// or the /documents/ask contract.
//
// Why this is needed: the browser blocks the React app (running on
// http://localhost:5173) from calling this backend (running on
// http://localhost:8080) because they are different "origins".
// Spring Boot does not allow cross-origin requests by default, so
// we have to explicitly say "requests from the frontend are okay".

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
