package com.automata.request.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.automata.common.utils.ValidationResult;

class RequestFieldLimitsTest {

	@Test
	void aNullValueIsWithinAnyLimit() {
		assertTrue(RequestFieldLimits.checkLength("The value", null, 1).isValid());
	}

	@Test
	void aValueAtTheLimitIsValid() {
		assertTrue(RequestFieldLimits.checkLength("The value", "abc", 3).isValid());
		assertTrue(RequestFieldLimits.checkLength("The value", "", 0).isValid());
	}

	@Test
	void aValueOverTheLimitIsInvalidWithAMessageThatNamesItAndTheLimit() {
		ValidationResult result = RequestFieldLimits.checkLength("The path", "abcd", 3);

		assertFalse(result.isValid());
		assertEquals("The path is too long: 4 characters, the limit is 3", result.getMessage());
	}

	@Test
	void charactersOutsideTheBasicPlaneCountOnce() {
		// the database counts characters (code points), not UTF-16 units
		String twoEmojis = "😀😀";

		assertEquals(4, twoEmojis.length());
		assertTrue(RequestFieldLimits.checkLength("The value", twoEmojis, 2).isValid());
		assertFalse(RequestFieldLimits.checkLength("The value", twoEmojis, 1).isValid());
	}

	@Test
	void aShortTextIsNotCutInAPreview() {
		assertEquals("token", RequestFieldLimits.preview("token"));
		assertEquals("a".repeat(40), RequestFieldLimits.preview("a".repeat(40)));
	}

	@Test
	void aLongTextIsCutInAPreview() {
		assertEquals("a".repeat(40) + "...", RequestFieldLimits.preview("a".repeat(41)));
	}

	@Test
	void aNullTextHasAnEmptyPreview() {
		assertEquals("", RequestFieldLimits.preview(null));
		assertNull(RequestFieldLimits.checkLength("x", null, 0).getMessage());
	}
}
