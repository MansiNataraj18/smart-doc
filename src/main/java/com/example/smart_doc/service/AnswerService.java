package com.example.smart_doc.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.smart_doc.model.AnswerResponse;
import com.example.smart_doc.model.SourceCitation;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;

// This service takes a question + the chunks we already retrieved
// from Qdrant, and asks the chat model to write an answer using
// ONLY those chunks. It now also builds the source citations, so
// the caller knows exactly which document/page/chunk the answer
// came from.

@Service
public class AnswerService {

    private final ChatModel chatModel;

    public AnswerService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public AnswerResponse generateAnswer(
            String question,
            List<EmbeddingMatch<TextSegment>> matches) {

        // 1. Stitch all the retrieved chunks together into one
        // block of text. This becomes the "context" the model
        // is allowed to use.
        String context = buildContext(matches);

        // 2. Build the prompt: instructions + context + question.
        String prompt = buildPrompt(context, question);

        // 3. Ask the chat model to generate the answer
        String answer = chatModel.chat(prompt);

        // 4. Build the citation list from the SAME chunks used above,
        // so the sources always match what the answer was based on.
        List<SourceCitation> sources = buildSources(matches);

        return new AnswerResponse(answer, sources);
    }

    private String buildContext(List<EmbeddingMatch<TextSegment>> matches) {

        StringBuilder context = new StringBuilder();

        for (EmbeddingMatch<TextSegment> match : matches) {

            context.append(match.embedded().text());
            context.append("\n\n");
        }

        return context.toString();
    }

    private String buildPrompt(String context, String question) {

        return """
                You are a document question-answering assistant.

                Answer the user's question using ONLY the information
                provided in the context below.

                If the answer cannot be found in the context,
                say that the information is not available in the
                provided documents.

                Do not make up information.

                Context:
                %s

                User question:
                %s

                Answer:
                """.formatted(context, question);
    }

    private List<SourceCitation> buildSources(
            List<EmbeddingMatch<TextSegment>> matches) {

        List<SourceCitation> sources = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : matches) {

            Metadata metadata = match.embedded().metadata();

            String document = metadata.getString("documentName");
            Integer page = metadata.getInteger("pageNumber");
            Integer chunk = metadata.getInteger("chunkIndex");

            // Only add a citation if we actually have all three
            // pieces of information -- an incomplete citation is
            // worse than no citation.
            if (document != null && page != null && chunk != null) {

                sources.add(
                        new SourceCitation(document, page, chunk)
                );
            }
        }

        return sources;
    }
}
