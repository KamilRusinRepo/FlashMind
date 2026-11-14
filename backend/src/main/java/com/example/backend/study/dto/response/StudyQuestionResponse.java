package com.example.backend.study.dto.response;

import com.example.backend.study.StudyAnswer;

public record StudyQuestionResponse(Long id, Long flashcardId, int position, String question,
                                    String exampleSentence, String imageUrl) {
    public static StudyQuestionResponse from(StudyAnswer answer) {
        return new StudyQuestionResponse(answer.getId(), answer.getFlashcard() == null ? null : answer.getFlashcard().getId(),
                answer.getPosition(), answer.getQuestionSnapshot(), answer.getExampleSentenceSnapshot(), answer.getImageUrlSnapshot());
    }
}
