package com.example.backend.auth.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordByteLengthValidator implements ConstraintValidator<PasswordByteLength, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // BCrypt limits passwords by byte count, not character count.
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
