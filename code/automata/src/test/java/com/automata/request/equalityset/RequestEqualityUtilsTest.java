package com.automata.request.equalityset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.request.Request;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.comparator.Modifier;
import com.automata.request.comparator.hash.HashTarget;

class RequestEqualityUtilsTest {

	private static Request request() {
		Request request = new Request();
		request.setMethod(Method.POST);
		request.setComputatedPath("/api/users/3@INT");
		request.setContentType(RequestContentType.JSON);
		request.setNumberOfProperties(4);
		return request;
	}

	private static Modifier modifier(HashTarget target) {
		return Modifier.builder().target(target).priority(1L).build();
	}

	@Test
	void aliasIsMethodAndPath() {
		assertEquals("POST:/api/users/3@INT", RequestEqualityUtils.computeAlias(request(), List.of(modifier(HashTarget.PATH))));
	}

	@Test
	void aliasIncludesTheExtensionWhenThereIsOne() {
		Request request = request();
		request.setExtension(".json");

		assertEquals("POST:/api/users/3@INT:.json", RequestEqualityUtils.computeAlias(request, List.of()));
	}

	@Test
	void anEmptyExtensionIsLeftOut() {
		Request request = request();
		request.setExtension("");

		assertEquals("POST:/api/users/3@INT", RequestEqualityUtils.computeAlias(request, List.of()));
	}

	@Test
	void aBodyModifierAddsTheContentTypeAndThePropertyCount() {
		assertEquals("POST:/api/users/3@INT:JSON:4",
				RequestEqualityUtils.computeAlias(request(), List.of(modifier(HashTarget.METHOD), modifier(HashTarget.BODY))));
	}

	@Test
	void aBodyModifierOfARequestWithoutContentTypeShowsNull() {
		Request request = request();
		request.setContentType(null);

		assertEquals("POST:/api/users/3@INT:null:4", RequestEqualityUtils.computeAlias(request, List.of(modifier(HashTarget.BODY))));
	}
}
