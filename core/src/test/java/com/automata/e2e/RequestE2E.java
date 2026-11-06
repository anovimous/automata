package com.automata.e2e;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.automata.agentcontext.AiRequestIngestionController;
import com.automata.agentcontext.AiRequestIngestionService;
import com.automata.integration.RepositoryIntegrationTest;
import com.automata.request.BurpAdapterService;
import com.automata.request.RequestBurpIntegrationController;
import com.automata.request.RequestController;
import com.automata.request.RequestService;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.comparator.hash.HashStrategyResolver;
import com.automata.request.comparator.hash.strategy.BodyHashStrategy;
import com.automata.request.comparator.hash.strategy.MethodHashStrategy;
import com.automata.request.comparator.hash.strategy.PathHashStrategy;
import com.automata.request.comparator.hash.strategy.QueryStringHashStrategy;
import com.automata.request.equalityset.RequestEqualityService;
import com.automata.request.header.HeaderService;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
import com.automata.response.ResponseController;
import com.automata.response.ResponseService;

/**
 * The real controllers, services, repositories, queries and schema, on a real PostgreSQL (see
 * {@link RepositoryIntegrationTest}); only the object storage of the large bodies is replaced.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@RepositoryIntegrationTest
@Import({ RequestController.class, RequestBurpIntegrationController.class, ResponseController.class, AiRequestIngestionController.class, AiRequestIngestionService.class, BurpAdapterService.class, RequestService.class,
		ResponseService.class, PathService.class, QueryParameterService.class, BodyPropertyService.class, HeaderService.class,
		RequestEqualityService.class, HashStrategyResolver.class, MethodHashStrategy.class, PathHashStrategy.class,
		QueryStringHashStrategy.class, BodyHashStrategy.class })
public @interface RequestE2E {
}
