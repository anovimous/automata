package com.automata.job.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.job.domain.model.embedded.PredicateTargetComponent;
import com.automata.request.Request;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.parameter.QueryParameter;

class RequestPredicateTargetComponentGetterTest {

	private static Request request() {
		Request request = Request.of(1L);
		request.setMethod(Method.GET);
		request.setComputatedPath("/a/b");
		request.setExtension("json");
		request.setParameters(List.of(QueryParameter.of("a", "1"), QueryParameter.of("b", "2")));
		return request;
	}

	@Test
	void contentTypeReturnsRawMime() {
		Request request = request();
		request.setContentType(RequestContentType.JSON);

		assertEquals("application/json",
				RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.CONTENT_TYPE, request));
	}

	@Test
	void contentTypeIsEmptyWhenNull() {
		Request request = request();
		request.setContentType(null);

		assertEquals("", RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.CONTENT_TYPE, request));
	}

	@Test
	void queryStringIsComposed() {
		assertEquals("?a=1&b=2",
				RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.QUERYSTRING, request()));
	}

	@Test
	void queryStringIsEmptyWithoutParameters() {
		Request request = request();
		request.setParameters(List.of());

		assertEquals("", RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.QUERYSTRING, request));
	}

	@Test
	void pathMethodAndExtension() {
		Request request = request();

		assertEquals("/a/b", RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.PATH, request));
		assertEquals("GET", RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.METHOD, request));
		assertEquals("json", RequestPredicateTargetComponentGetter.get(PredicateTargetComponent.EXTENSION, request));
	}

}
