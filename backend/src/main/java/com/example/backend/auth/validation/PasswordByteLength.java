package com.example.backend.auth.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordByteLengthValidator.class)
public @interface PasswordByteLength {

    String message() default "Password must not exceed 72 UTF-8 bytes.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
