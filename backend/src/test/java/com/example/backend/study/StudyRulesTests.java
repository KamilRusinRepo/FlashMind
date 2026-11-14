package com.example.backend.study;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudyRulesTests {
    @Test
    void acceptsTrimmedCaseInsensitiveAlternativeAnswers() {
        assertTrue(StudyRules.matches("  PRZYZWYCZAJENIE  ", " nawyk, przyzwyczajenie "));
        assertTrue(StudyRules.matches("CIEKAWOŚĆ", "ciekawość"));
    }

    @Test
    void rejectsEmptyPartialAndDifferentAnswers() {
        assertFalse(StudyRules.matches(" ", "curiosity"));
        assertFalse(StudyRules.matches("cur", "curiosity"));
        assertFalse(StudyRules.matches("ciekawosc", "ciekawość"));
    }

    @Test
    void hintsUseOneThirdOfFirstVariantWithoutSplittingUnicodeCharacters() {
        assertEquals("cie", StudyRules.hint(" ciekawość, zainteresowanie"));
        assertEquals("😀a", StudyRules.hint("😀abcd, other"));
        assertEquals("a", StudyRules.hint("a"));
    }

    @Test
    void correctAnswersRaiseMasteryResetWrongStreakAndIncreaseIntervals() {
        assertEquals(new StudyRules.ReviewPlan(2, 0, 3), StudyRules.schedule(true, 0, 0));
        assertEquals(new StudyRules.ReviewPlan(3, 0, 7), StudyRules.schedule(true, 2, 1));
        assertEquals(new StudyRules.ReviewPlan(4, 0, 14), StudyRules.schedule(true, 3, 0));
        assertEquals(new StudyRules.ReviewPlan(4, 0, 14), StudyRules.schedule(true, 4, 0));
    }

    @Test
    void wrongAnswersReviewTomorrowAndLowerMasteryAfterTwoFailures() {
        assertEquals(new StudyRules.ReviewPlan(1, 1, 1), StudyRules.schedule(false, 0, 0));
        assertEquals(new StudyRules.ReviewPlan(3, 1, 1), StudyRules.schedule(false, 3, 0));
        assertEquals(new StudyRules.ReviewPlan(2, 0, 1), StudyRules.schedule(false, 3, 1));
        assertEquals(new StudyRules.ReviewPlan(1, 0, 1), StudyRules.schedule(false, 1, 1));
    }
}
