package com.example.smart_doc.model;

import java.util.List;

// This is the object /documents/ask now returns: the answer text,
// plus the list of sources it was built from.

public class AnswerResponse {

    private String answer;
    private List<SourceCitation> sources;

    public AnswerResponse(
            String answer,
            List<SourceCitation> sources) {

        this.answer = answer;
        this.sources = sources;
    }

    public String getAnswer() {
        return answer;
    }

    public List<SourceCitation> getSources() {
        return sources;
    }
}
