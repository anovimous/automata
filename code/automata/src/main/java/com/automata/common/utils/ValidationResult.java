package com.automata.common.utils;

import java.util.function.Supplier;

import lombok.Getter;

@Getter
public final class ValidationResult {

	private final boolean valid;

	private final String message;

	private ValidationResult(boolean valid) {
		this.valid = valid;
		this.message = null;
	}

	private ValidationResult(boolean valid, String message) {
		this.valid = valid;
		this.message = message;
	}

	public static ValidationResult valid() {
		return new ValidationResult(true);
	}

	public static ValidationResult invalid() {
		return new ValidationResult(false);
	}

	public static ValidationResult invalid(String message) {
		return new ValidationResult(false, message);
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
