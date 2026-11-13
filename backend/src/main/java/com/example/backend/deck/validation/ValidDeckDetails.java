package com.example.backend.deck.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DeckDetailsValidator.class)
public @interface ValidDeckDetails {
    String message() default "Please check the deck details.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
