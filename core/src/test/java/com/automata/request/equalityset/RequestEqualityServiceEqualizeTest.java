package com.automata.request.equalityset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.comparator.Comparator;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.comparator.Modifier;
import com.automata.request.comparator.hash.HashStrategyResolver;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.request.comparator.hash.strategy.HashStrategy;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

@ExtendWith(MockitoExtension.class)
class RequestEqualityServiceEqualizeTest {

	@Mock
	EqualizationRepository equalizationRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	HostRepository hostRepo;
	@Mock
	ComparatorRepository comparatorRepo;
	@Mock
	RequestEqualitySetRepository equalitySetRepo;
	@Mock
	RequestEqualityRelationRepository requestEqualityRelationRepo;
	@Mock
	HashStrategyResolver resolver;

	@InjectMocks
	RequestEqualityService service;

	private Request request(Long id) {
		Request request = Request.of(id);
		request.setMethod(com.automata.request.common.enums.Method.GET);
		request.setComputatedPath("/a");
		return request;
	}

	// A second run: request 20 is already in set 30 (the pair is (set id, request id)), request 21
	// is new and hashes like 20, so it joins set 30 and 20 is not handled again
	@Test
	@SuppressWarnings("unchecked")
	void equalizeAgainAddsANewRequestToTheSetOfItsEqualAndSkipsTheRequestsAlreadyInASet() {
		Host host = new Host();
		host.setId(1L);
		Modifier modifier = Modifier.builder().target(HashTarget.METHOD).priority(1L).build();
		Comparator comparator = Comparator.builder().id(7L).modifiers(List.of(modifier)).build();
		Equalization equalization = Equalization.builder().id(5L).host(host).comparator(comparator).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(30L).alias("GET:/a").equalization(equalization)
				.numberOfRequests(1).build();
		Request existing = request(20L);
		Request fresh = request(21L);
		HashStrategy strategy = org.mockito.Mockito.mock(HashStrategy.class);
		HashCode sameHash = Hashing.murmur3_128().hashInt(1);

		when(equalizationRepo.findById(5L)).thenReturn(Optional.of(equalization));
		when(comparatorRepo.findById(7L)).thenReturn(Optional.of(comparator));
		when(requestRepo.findIdsByHost(host)).thenReturn(new HashSet<>(Set.of(20L, 21L)));
		when(requestEqualityRelationRepo.findRequestEqualitySetPairs(5L))
				.thenReturn(List.of(new RequestEqualityRelationSummaryInternalDto(30L, 20L)));
		when(equalitySetRepo.findAllById(any())).thenAnswer(inv -> {
			assertEquals(Set.of(30L), Set.copyOf((java.util.Collection<Long>) inv.getArgument(0)));
			return List.of(set);
		});
		// the representatives are asked first (request 20 of set 30), then the requests not in a set yet
		when(requestRepo.findAllById(any())).thenAnswer(inv -> {
			Set<Long> ids = Set.copyOf((java.util.Collection<Long>) inv.getArgument(0));
			return ids.equals(Set.of(20L)) ? List.of(existing) : ids.equals(Set.of(21L)) ? List.of(fresh) : List.of();
		});
		when(resolver.resolve(HashTarget.METHOD)).thenReturn(strategy);
		when(strategy.hashStrategyTarget(any(), any())).thenReturn(sameHash);

		service.equalizeRequestsIntoEqualityGroups(5L);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RequestEqualityRelation>> relations = ArgumentCaptor.forClass(List.class);
		verify(requestEqualityRelationRepo).saveAll(relations.capture());
		assertEquals(1, relations.getValue().size());
		assertEquals(21L, relations.getValue().get(0).getRequest().getId());
		assertEquals(30L, relations.getValue().get(0).getSet().getId());
		assertEquals(2, set.getNumberOfRequests());
		verify(equalitySetRepo, never()).save(any());
	}

}
