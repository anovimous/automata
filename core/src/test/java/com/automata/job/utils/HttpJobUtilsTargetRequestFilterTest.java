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
import com.automata.job.domain.model.NarrowJobTargetRequest;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@SuppressWarnings({ "unchecked", "rawtypes" })
class HttpJobUtilsTargetRequestFilterTest {

	private final Root<HttpJob> root = mock(Root.class);
	private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
	private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

	@Test
	void jobsThatTargetedTheRequestAreFilteredWithAnExistsSubquery() {
		Subquery<Long> subquery = mock(Subquery.class);
		Root<NarrowJobTargetRequest> target = mock(Root.class);
		Path jobPath = mock(Path.class);
		Path targetRequestPath = mock(Path.class);
		Path requestIdPath = mock(Path.class);
		when(query.subquery(Long.class)).thenReturn((Subquery) subquery);
		when(subquery.from(NarrowJobTargetRequest.class)).thenReturn(target);
		when(target.get("job")).thenReturn(jobPath);
		when(target.get("targetRequest")).thenReturn(targetRequestPath);
		when(targetRequestPath.get("id")).thenReturn(requestIdPath);
		when(subquery.select(any())).thenReturn(subquery);

		Specification<HttpJob> spec = HttpJobUtils
				.buildSpecification(new HttpJobFilter(null, null, null, null, Set.of(), null, null, 9L));
		spec.toPredicate(root, query, cb);

		verify(cb).equal(jobPath, root);
		verify(cb).equal(requestIdPath, 9L);
		verify(cb).exists(subquery);
	}

	@Test
	void withoutATargetRequestNothingIsFilteredOnIt() {
		Specification<HttpJob> spec = HttpJobUtils
				.buildSpecification(new HttpJobFilter(null, null, null, null, Set.of(), null, null, null));
		spec.toPredicate(root, query, cb);

		verify(query, never()).subquery(any());
		verify(cb, never()).exists(any());
	}

}
