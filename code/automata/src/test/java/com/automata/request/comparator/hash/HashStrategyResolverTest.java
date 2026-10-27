package com.automata.request.comparator.hash;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.request.comparator.hash.strategy.BodyHashStrategy;
import com.automata.request.comparator.hash.strategy.HashStrategy;
import com.automata.request.comparator.hash.strategy.MethodHashStrategy;
import com.automata.request.comparator.hash.strategy.PathHashStrategy;
import com.automata.request.comparator.hash.strategy.QueryStringHashStrategy;

class HashStrategyResolverTest {

	private final MethodHashStrategy method = new MethodHashStrategy();
	private final PathHashStrategy path = new PathHashStrategy();
	private final QueryStringHashStrategy query = new QueryStringHashStrategy();
	private final BodyHashStrategy body = new BodyHashStrategy();

	@Test
	void resolvesTheStrategyOfEveryTarget() {
		HashStrategyResolver resolver = new HashStrategyResolver(List.of(method, path, query, body));

		assertSame(method, resolver.resolve(HashTarget.METHOD));
		assertSame(path, resolver.resolve(HashTarget.PATH));
		assertSame(query, resolver.resolve(HashTarget.QUERYSTRING));
		assertSame(body, resolver.resolve(HashTarget.BODY));
	}

	@Test
	void everyHashTargetHasAStrategyInTheApplication() {
		HashStrategyResolver resolver = new HashStrategyResolver(List.of(method, path, query, body));

		for (HashTarget target : HashTarget.values())
			resolver.resolve(target);
	}

	@Test
	void aTargetWithoutStrategyIsRefused() {
		HashStrategyResolver resolver = new HashStrategyResolver(List.of(method));

		assertThrows(IllegalArgumentException.class, () -> resolver.resolve(HashTarget.BODY));
	}

	@Test
	void twoStrategiesForTheSameTargetFailAtStartup() {
		List<HashStrategy> strategies = List.of(method, new MethodHashStrategy());

		assertThrows(IllegalStateException.class, () -> new HashStrategyResolver(strategies));
	}
}
