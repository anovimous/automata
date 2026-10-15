package com.automata.request.comparator.hash.strategy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import com.automata.request.Request;
import com.automata.request.body.BodyParseResult;
import com.automata.request.body.BodyUtils;
import com.automata.request.common.enums.RequestContentType;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

class BodyHashStrategyTest {

	private final BodyHashStrategy strategy = new BodyHashStrategy();

	private HashCode hash(BodyParseResult parsed, RequestContentType type) {
		Request request = new Request();
		request.setContentType(type);
		request.setBodyProperties(parsed.getBodyProperties());
		return strategy.hashStrategyTarget(request, Hashing.murmur3_128());
	}

	private HashCode hashJson(String json) {
		return hash(BodyUtils.parseJsonBody(json), RequestContentType.JSON);
	}

	private HashCode hashForm(String form) {
		return hash(BodyUtils.parseFormBody(form), RequestContentType.FORM);
	}

	// Does not throw

	@Test
	void flatJsonObject_doesNotThrow() {
		assertDoesNotThrow(() -> hashJson("{\"a\":1,\"b\":\"x\"}"));
	}

	@Test
	void nestedJsonObject_doesNotThrow() {
		assertDoesNotThrow(() -> hashJson("{\"customer\":{\"id\":\"c1\",\"roles\":[\"USER\",\"ADMIN\"]}}"));
	}

	@Test
	void formBody_doesNotThrow() {
		assertDoesNotThrow(() -> hashForm("a=1&b=2"));
	}

	@Test
	void topLevelJsonArrayOfObjects_doesNotThrow() {
		assertDoesNotThrow(() -> hashJson("[{\"customer\":{\"roles\":[\"USER\"]}}]"));
	}

	@Test
	void emptyBody_doesNotThrow() {
		assertDoesNotThrow(() -> hash(new BodyParseResult(), null));
	}

	// Key order, case, structure

	@Test
	void sameKeysDifferentOrder_hashEqual() {
		assertEquals(hashJson("{\"a\":1,\"b\":2}"), hashJson("{\"b\":2,\"a\":1}"));
	}

	@Test
	void nestedSameKeysDifferentOrder_hashEqual() {
		assertEquals(hashJson("{\"u\":{\"x\":1,\"y\":2},\"v\":3}"), hashJson("{\"v\":3,\"u\":{\"y\":2,\"x\":1}}"));
	}

	@Test
	void keyCaseIsIgnored() {
		assertEquals(hashJson("{\"Name\":1}"), hashJson("{\"name\":1}"));
	}

	@Test
	void differentKeySplits_hashDiffer() {
		assertNotEquals(hashJson("{\"ab\":1,\"c\":2}"), hashJson("{\"a\":1,\"bc\":2}"));
	}

	@Test
	void differentStructure_hashDiffer() {
		assertNotEquals(hashJson("{\"a\":{\"b\":1}}"), hashJson("{\"a\":1,\"b\":1}"));
	}

	@Test
	void differentKeys_hashDiffer() {
		assertNotEquals(hashJson("{\"a\":1}"), hashJson("{\"b\":1}"));
	}

	// Values and value types are ignored

	@Test
	void valuesAndTypesAreIgnored() {
		assertEquals(hashJson("{\"id\":1}"), hashJson("{\"id\":\"x\"}"));
		assertEquals(hashJson("{\"id\":1}"), hashJson("{\"id\":null}"));
	}

	@Test
	void emptyObjectEqualsScalarWithSameName() {
		assertEquals(hashJson("{\"meta\":{}}"), hashJson("{\"meta\":1}"));
	}

	// Arrays: represented by their name only

	@Test
	void arrayLengthIsIgnored() {
		assertEquals(hashJson("{\"roles\":[\"A\"]}"), hashJson("{\"roles\":[\"A\",\"B\",\"C\"]}"));
	}

	@Test
	void arrayElementOrderIsIgnored() {
		assertEquals(hashJson("{\"roles\":[\"A\",\"B\"]}"), hashJson("{\"roles\":[\"B\",\"A\"]}"));
	}

	@Test
	void emptyAndNonEmptyArrayHashEqual() {
		assertEquals(hashJson("{\"tags\":[]}"), hashJson("{\"tags\":[\"x\"]}"));
	}

	@Test
	void objectsInsideArrayAreIgnored() {
		assertEquals(hashJson("{\"items\":[{\"sku\":1}]}"),
				hashJson("{\"items\":[{\"price\":2},{\"qty\":3,\"sku\":4}]}"));
	}

	@Test
	void nestedArrayKeepsPathUpToArrayName() {
		assertEquals(hashJson("{\"order\":{\"items\":[{\"sku\":1}]}}"), hashJson("{\"order\":{\"items\":[1,2]}}"));
		assertNotEquals(hashJson("{\"order\":{\"items\":[1]}}"), hashJson("{\"items\":[1]}"));
	}

	@Test
	void arrayOfArraysCutAtFirstBracket() {
		assertEquals(hashJson("{\"a\":[[1,2],[3]]}"), hashJson("{\"a\":[]}"));
	}

	@Test
	void arrayAndOtherKeysTogether() {
		assertEquals(hashJson("{\"id\":1,\"roles\":[\"A\",\"B\"]}"), hashJson("{\"roles\":[\"C\"],\"id\":2}"));
		assertNotEquals(hashJson("{\"id\":1,\"roles\":[\"A\"]}"), hashJson("{\"id\":1}"));
	}

	@Test
	void rootArrayDiffersFromEmptyBody() {
		assertNotEquals(hashJson("[{\"id\":1}]"), hash(new BodyParseResult(), RequestContentType.JSON));
	}

	@Test
	void rootArrayContentsAreIgnored() {
		assertEquals(hashJson("[{\"id\":1}]"), hashJson("[1,2,3]"));
	}

	// Form bodies keep their full keys

	@Test
	void formKeyOrderIsIgnored() {
		assertEquals(hashForm("a=1&b=2"), hashForm("b=9&a=8"));
	}

	@Test
	void formKeysWithBracketsStayDistinct() {
		assertNotEquals(hashForm("user[name]=x"), hashForm("user[email]=x"));
	}

	@Test
	void formRepeatedKeyCollapses() {
		assertEquals(hashForm("a=1&a=2"), hashForm("a=3"));
	}

	@Test
	void formAndJsonWithSameKeysHashEqual() {
		assertEquals(hashForm("a=1&b=2"), hashJson("{\"a\":1,\"b\":2}"));
	}
}
