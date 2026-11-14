package com.example.backend.study.dto.response;

import com.example.backend.study.StudyAnswer;
import com.example.backend.study.StudyRules;

public record StudyQuestionResponse(Long id, Long flashcardId, int position, String question,
                                    String exampleSentence, String imageUrl, boolean usedHint, String hint,
                                    StudyAnswerResultResponse result) {
    public static StudyQuestionResponse from(StudyAnswer answer) {
        return new StudyQuestionResponse(answer.getId(), answer.getFlashcard() == null ? null : answer.getFlashcard().getId(),
                answer.getPosition(), answer.getQuestionSnapshot(), answer.getExampleSentenceSnapshot(), answer.getImageUrlSnapshot(),
                answer.isUsedHint(), answer.isUsedHint() && !answer.isAnswered() ? StudyRules.hint(answer.getAnswerSnapshot()) : null,
                answer.isAnswered() ? StudyAnswerResultResponse.from(answer) : null);
    }
}
