package com.automata.request.common;

import com.automata.common.utils.ValidationResult;

/**
 * What the columns which hold text taken from a request or a response can
 * store: a limited number of characters, and no NUL character. A value which
 * does not fit is refused with a clear message instead of ending in a database
 * error, which would lose the whole message.
 */
public final class RequestFieldLimits {

	public static final int MAX_COMPUTATED_PATH = 500;

	public static final int MAX_EXTENSION = 500;

	public static final int MAX_PARAMETER_NAME = 500;

	public static final int MAX_PARAMETER_VALUE = 2000;

	public static final int MAX_PATH_VARIABLE_VALUE = 2000;

	public static final int MAX_HEADER_NAME = 2000;

	public static final int MAX_BODY_PROPERTY_PATH = 2000;

	private static final int PREVIEW_LENGTH = 40;

	private RequestFieldLimits() {
	}

	/**
	 * @param what  what the value is, for the message
	 * @param value null is within any limit
	 * @return invalid when the value has more than {@code max} characters
	 */
	public static ValidationResult checkLength(String what, String value, int max) {

		if (value == null)
			return ValidationResult.valid();

		// the database counts characters, not UTF-16 units
		int length = value.codePointCount(0, value.length());

		if (length <= max)
			return ValidationResult.valid();

		return ValidationResult
				.invalid(String.format("%s is too long: %d characters, the limit is %d", what, length, max));

	}

	/**
	 * PostgreSQL cannot store a NUL character in a text, and one reaches the
	 * parsed copy when a "%00" or a "\u0000" is decoded.
	 *
	 * @param what  what the value is, for the message
	 * @param value null has no NUL character
	 */
	public static ValidationResult checkNoNul(String what, String value) {

		if (value == null || value.indexOf('\u0000') < 0)
			return ValidationResult.valid();

		return ValidationResult.invalid(what + " contains a NUL character (%00), which cannot be stored");

	}

	/**
	 * The start of a name, so that a message does not repeat a very long text.
	 */
	public static String preview(String text) {

		if (text == null)
			return "";

		return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "...";

	}

}
