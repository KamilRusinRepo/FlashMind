package com.example.backend.flashcard.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ImageUrlValidator.class)
public @interface ImageUrl {
    String message() default "Enter an absolute HTTP or HTTPS image URL without embedded credentials.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
