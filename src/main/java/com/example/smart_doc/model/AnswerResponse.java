package com.example.smart_doc.model;

import java.util.List;

/** The JSON body returned by POST /documents/ask: an answer plus its citations. */
public class AnswerResponse {

    private String answer;
    private List<SourceCitation> sources;

    /** Creates a completed answer with its supporting citations. */
    public AnswerResponse(
            String answer,
            List<SourceCitation> sources) {

        this.answer = answer;
        this.sources = sources;
    }

    /** @return the answer text */
    public String getAnswer() {
        return answer;
    }

    /** @return the citations for this answer */
    public List<SourceCitation> getSources() {
        return sources;
    }
}
