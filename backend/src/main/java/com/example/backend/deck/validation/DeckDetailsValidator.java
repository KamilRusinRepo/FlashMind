package com.example.backend.deck.validation;

import java.net.URI;

import com.example.backend.deck.DeckType;
import com.example.backend.deck.dto.request.SaveDeckRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DeckDetailsValidator implements ConstraintValidator<ValidDeckDetails, SaveDeckRequest> {
    @Override
    public boolean isValid(SaveDeckRequest request, ConstraintValidatorContext context) {
        if (request == null) return true;
        boolean valid = true;
        if (request.deckType() == DeckType.LANGUAGE && request.targetLanguage() == null) {
            fieldError(context, "targetLanguage", "Target language is required for a language deck.");
            valid = false;
        }
        if (request.backgroundUrl() != null && !validImageUrl(request.backgroundUrl())) {
            fieldError(context, "backgroundUrl", "Enter an absolute http or https URL without embedded credentials.");
            valid = false;
        }
        return valid;
    }

    private boolean validImageUrl(String value) {
        try {
            var uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void fieldError(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
    }
}
