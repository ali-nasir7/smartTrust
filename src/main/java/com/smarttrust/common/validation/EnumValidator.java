package com.smarttrust.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;

public class EnumValidator implements ConstraintValidator<ValidEnum, Enum<?>> {

    private Class<? extends Enum<?>> enumClass;

    @Override
    public void initialize(ValidEnum annotation) {
        this.enumClass = annotation.enumClass();
    }

    @Override
    public boolean isValid(Enum<?> value, ConstraintValidatorContext context) {
        if (value == null) return true; // use @NotNull for null check
        return Arrays.stream(enumClass.getEnumConstants())
                .anyMatch(e -> e.name().equals(value.name()));
    }
}
