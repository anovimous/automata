package com.automata.request.comparator.hash.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.request.Request;
import com.automata.request.common.enums.Method;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.request.parameter.QueryParameter;
import com.automata.testsupport.KnownBug;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

class SimpleHashStrategiesTest {

	private static final HashFunction FUNCTION = Hashing.murmur3_128();

	private final MethodHashStrategy method = new MethodHashStrategy();
	private final PathHashStrategy path = new PathHashStrategy();
	private final QueryStringHashStrategy query = new QueryStringHashStrategy();

	private static Request request(Method method, String path, String... parameterNames) {
		Request request = new Request();
		request.setMethod(method);
		request.setComputatedPath(path);
		List<QueryParameter> parameters = new ArrayList<>();
		for (String name : parameterNames)
			parameters.add(QueryParameter.of(name, "v"));
		request.setParameters(parameters);
		return request;
	}

	@Test
	void eachStrategySupportsItsOwnTarget() {
		assertEquals(HashTarget.METHOD, method.supports());
		assertEquals(HashTarget.PATH, path.supports());
		assertEquals(HashTarget.QUERYSTRING, query.supports());
	}

	// ---- method ----

	@Test
	void sameMethodSameHash() {
		assertEquals(method.hashStrategyTarget(request(Method.GET, "/a"), FUNCTION),
				method.hashStrategyTarget(request(Method.GET, "/b"), FUNCTION));
	}

	@Test
	void differentMethodsDifferentHash() {
		assertNotEquals(method.hashStrategyTarget(request(Method.GET, "/a"), FUNCTION),
				method.hashStrategyTarget(request(Method.POST, "/a"), FUNCTION));
	}

	// ---- path ----

	@Test
	void samePathSameHash() {
		assertEquals(path.hashStrategyTarget(request(Method.GET, "/a/1@INT"), FUNCTION),
				path.hashStrategyTarget(request(Method.POST, "/a/1@INT"), FUNCTION));
	}

	@Test
	void differentPathsDifferentHash() {
		assertNotEquals(path.hashStrategyTarget(request(Method.GET, "/a"), FUNCTION),
				path.hashStrategyTarget(request(Method.GET, "/b"), FUNCTION));
	}

	@Test
	void pathHashIsCaseSensitive() {
		assertNotEquals(path.hashStrategyTarget(request(Method.GET, "/Users"), FUNCTION),
				path.hashStrategyTarget(request(Method.GET, "/users"), FUNCTION));
	}

	// ---- query string ----

	@Test
	void parameterOrderIsIgnored() {
		assertEquals(query.hashStrategyTarget(request(Method.GET, "/a", "x", "y"), FUNCTION),
				query.hashStrategyTarget(request(Method.GET, "/a", "y", "x"), FUNCTION));
	}

	@Test
	void parameterValuesAreIgnored() {
		Request one = request(Method.GET, "/a", "x");
		one.getParameters().get(0).setValue("1");
		Request two = request(Method.GET, "/a", "x");
		two.getParameters().get(0).setValue("2");

		assertEquals(query.hashStrategyTarget(one, FUNCTION), query.hashStrategyTarget(two, FUNCTION));
	}

	@Test
	void differentParameterNamesDifferentHash() {
		assertNotEquals(query.hashStrategyTarget(request(Method.GET, "/a", "x"), FUNCTION),
				query.hashStrategyTarget(request(Method.GET, "/a", "y"), FUNCTION));
	}

	@Test
	void noParametersHashTheSameAsEachOther() {
		assertEquals(query.hashStrategyTarget(request(Method.GET, "/a"), FUNCTION),
				query.hashStrategyTarget(request(Method.POST, "/b"), FUNCTION));
	}

	@Test
	void aRepeatedParameterDiffersFromASingleOne() {
		assertNotEquals(query.hashStrategyTarget(request(Method.GET, "/a", "id", "id"), FUNCTION),
				query.hashStrategyTarget(request(Method.GET, "/a", "id"), FUNCTION));
	}

	@Test
	@KnownBug("BUG-026")
	void oneParameterIsNotTheSameAsTwoWhoseNamesConcatenateToIt() {
		// ?ab=1 and ?a=1&b=2 are different shapes
		assertNotEquals(query.hashStrategyTarget(request(Method.GET, "/a", "ab"), FUNCTION),
				query.hashStrategyTarget(request(Method.GET, "/a", "a", "b"), FUNCTION));
	}
}
