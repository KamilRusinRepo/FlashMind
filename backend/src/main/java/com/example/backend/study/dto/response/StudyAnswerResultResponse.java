package com.example.backend.study.dto.response;

import java.time.Instant;
import com.example.backend.study.StudyAnswer;

public record StudyAnswerResultResponse(String userAnswer, boolean correct, boolean usedHint, boolean skipped,
                                        Instant answeredAt, String answer, String translatedExample) {
    public static StudyAnswerResultResponse from(StudyAnswer answer) {
        return new StudyAnswerResultResponse(answer.getUserAnswer(), answer.getCorrect(), answer.isUsedHint(), answer.isSkipped(),
                answer.getAnsweredAt(), answer.getAnswerSnapshot(), answer.getTranslatedExampleSnapshot());
    }
}
