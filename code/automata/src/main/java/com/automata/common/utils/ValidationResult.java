package com.automata.common.utils;

import java.util.function.Supplier;

public final class ValidationResult {

    private final boolean valid;

    private ValidationResult(boolean valid) {
        this.valid = valid;
    }

    public static ValidationResult valid() {
        return new ValidationResult(true);
    }

    public static ValidationResult invalid() {
        return new ValidationResult(false);
    }

    public void ifNotValidThrow(Supplier<? extends RuntimeException> exceptionSupplier) {
        if (!valid) {
            throw exceptionSupplier.get();
        }
    }

    public boolean isValid() {
        return valid;
    }
}
