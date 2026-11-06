package com.automata.finding;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.jpa.domain.Specification;

import com.automata.request.equalityset.RequestEqualityRelation;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@SuppressWarnings({ "unchecked", "rawtypes" })
class FindingUtilsTest {

	private final Root<Finding> root = mock(Root.class);
	private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
	private final CriteriaBuilder cb = mock(CriteriaBuilder.class);
	private final Path<Object> requestPath = mock(Path.class);
	private final Path<Object> requestIdPath = mock(Path.class);

	private void findingRequestIdPath() {
		doReturn(requestPath).when(root).get("request");
		doReturn(requestIdPath).when(requestPath).get("id");
	}

	@Test
	void noRequestsAndNoSetsMatchNothing() {
		Predicate none = mock(Predicate.class);
		when(cb.disjunction()).thenReturn(none);

		Predicate result = FindingUtils.inRequestsOrEqualitySets(List.of(), List.of()).toPredicate(root, query, cb);

		assertSame(none, result);
		verify(query, never()).subquery(any(Class.class));
	}

	@Test
	void onlyRequestsMatchTheirFindingsWithoutASubquery() {
		findingRequestIdPath();
		Predicate inRequests = mock(Predicate.class);
		Predicate or = mock(Predicate.class);
		doReturn(inRequests).when(requestIdPath).in(ArgumentMatchers.<java.util.Collection<?>>any());
		when(cb.or(new Predicate[] { inRequests })).thenReturn(or);

		Predicate result = FindingUtils.inRequestsOrEqualitySets(List.of(10L), List.of()).toPredicate(root, query, cb);

		assertSame(or, result);
		verify(query, never()).subquery(any(Class.class));
	}

	// The members subquery: relation.request.id selected where relation.set.id is in the sets
	private Subquery<Long> membersSubquery(Predicate setIn) {
		Subquery<Long> members = mock(Subquery.class);
		Root<RequestEqualityRelation> relation = mock(Root.class);
		Path<Object> relationPath = mock(Path.class);
		when(query.subquery(Long.class)).thenReturn((Subquery) members);
		when(members.from(RequestEqualityRelation.class)).thenReturn(relation);
		doReturn(relationPath).when(relation).get(any(String.class));
		doReturn(relationPath).when(relationPath).get(any(String.class));
		doReturn(setIn).when(relationPath).in(ArgumentMatchers.<java.util.Collection<?>>any());
		doReturn(members).when(members).select(any(Expression.class));
		return members;
	}

	@Test
	void setsMatchTheFindingsOfEveryMemberThroughASubquery() {
		findingRequestIdPath();
		Predicate setIn = mock(Predicate.class);
		Subquery<Long> members = membersSubquery(setIn);
		Predicate inMembers = mock(Predicate.class);
		Predicate or = mock(Predicate.class);
		doReturn(inMembers).when(requestIdPath).in(ArgumentMatchers.<Expression<?>>any());
		when(cb.or(new Predicate[] { inMembers })).thenReturn(or);

		Predicate result = FindingUtils.inRequestsOrEqualitySets(List.of(), List.of(30L)).toPredicate(root, query, cb);

		assertSame(or, result);
		verify(members).where(setIn);
		verify(requestIdPath, never()).in(ArgumentMatchers.<java.util.Collection<?>>any());
	}

	@Test
	void requestsAndSetsAreCombinedWithOr() {
		findingRequestIdPath();
		membersSubquery(mock(Predicate.class));
		Predicate inRequests = mock(Predicate.class);
		Predicate inMembers = mock(Predicate.class);
		Predicate or = mock(Predicate.class);
		doReturn(inRequests).when(requestIdPath).in(ArgumentMatchers.<java.util.Collection<?>>any());
		doReturn(inMembers).when(requestIdPath).in(ArgumentMatchers.<Expression<?>>any());
		when(cb.or(new Predicate[] { inRequests, inMembers })).thenReturn(or);

		Specification<Finding> spec = FindingUtils.inRequestsOrEqualitySets(List.of(10L), List.of(30L));

		assertSame(or, spec.toPredicate(root, query, cb));
	}

}
