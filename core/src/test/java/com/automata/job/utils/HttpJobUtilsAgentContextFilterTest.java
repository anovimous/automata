package com.automata.job.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.automata.job.api.dto.HttpJobFilter;
import com.automata.job.domain.model.HttpJob;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

@SuppressWarnings({ "unchecked", "rawtypes" })
class HttpJobUtilsAgentContextFilterTest {

	private final Root<HttpJob> root = mock(Root.class);
	private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
	private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
	private final Path contextPath = mock(Path.class);
	private final Path contextIdPath = mock(Path.class);

	@Test
	void jobsOfTheGivenAgentContextAreFiltered() {
		when(root.get("agentContext")).thenReturn(contextPath);
		when(contextPath.get("id")).thenReturn(contextIdPath);

		Specification<HttpJob> spec = HttpJobUtils
				.buildSpecification(new HttpJobFilter(null, null, null, null, Set.of(), null, 3L, null));
		spec.toPredicate(root, query, cb);

		verify(cb).equal(contextIdPath, 3L);
	}

	@Test
	void withoutAnAgentContextNothingIsFilteredOnIt() {
		Specification<HttpJob> spec = HttpJobUtils
				.buildSpecification(new HttpJobFilter(null, null, null, null, Set.of(), null, null, null));
		spec.toPredicate(root, query, cb);

		verify(root, never()).get("agentContext");
		verify(cb, never()).equal(any(), any(Object.class));
	}

}
