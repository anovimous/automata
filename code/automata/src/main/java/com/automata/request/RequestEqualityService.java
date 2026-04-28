package com.automata.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.common.dto.RequestsEqualizationDto;
import com.automata.request.comparator.Comparator;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.comparator.Modifier;
import com.automata.request.comparator.hash.HashStrategyResolver;
import com.automata.request.comparator.hash.strategy.HashStrategy;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestEqualityService {

	private final HashFunction HASHFUNCTION = Hashing.murmur3_128();

	private final RequestRepository requestRepo;

	private final HostRepository hostRepo;

	private final ComparatorRepository comparatorRepo;

	private final RequestEqualitySetRepository equalitySetRepo;

	private final HashStrategyResolver resolver;

	@Transactional
	public void equalizeRequestsIntoEqualityGroups(RequestsEqualizationDto dto) {

		// NOTE: This operation until now only works correctly when there is 0 equality
		// sets for the
		// current host, aka never equalized before. Implement feature below.

		// TODO(FEATURE):
		// 1. Find all equalitySets under the same Host and Comparator
		// 2. Find all request ids which are under these equalitySets
		// 3.Remove past equalized request ids from the target ids and into their final
		// equalized and combined form
		// 4. Equalize the new ones into their final form
		// 5. Merge the final new equality sets into the old ones if any were equal,
		// else create a new equalitySet in the DB

		Host host = hostRepo.findById(dto.hostId()).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		Comparator comparator = comparatorRepo.findById(dto.comparatorId())
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

		List<Long> requestsIds = dto.requestsIds() != null && !dto.requestsIds().isEmpty() ? dto.requestsIds()
				: requestRepo.findIdsByHost(host);

		if (requestsIds.size() > 200)
			throw new RuntimeException(
					"Only a max of 200 requests can be equalized at a time, you either provided more than 200 ids or the host contains more than that in case no ids were provided");

		List<Modifier> modifiers = comparator.getModifiers().stream()
				.sorted((first, second) -> Long.compare(first.getPriority(), second.getPriority()))
				.collect(Collectors.toList());

		List<Request> requests = requestRepo.findAllById(requestsIds);

		List<List<Request>> requestEqualityGroups = this.groupOnModifiers(requests, modifiers);

		requestEqualityGroups.forEach((group) -> {

			RequestEqualitySet set = new RequestEqualitySet();

			set.setComparator(comparator);

			set.setCurrentNumberOfRequests(group.size());

			equalitySetRepo.save(set);

			set.setHost(host);

			set.setRequests(new HashSet<>(group));

		});

	}

	private List<List<Request>> groupOnModifiers(List<Request> requests, List<Modifier> modifiers) {

		List<List<Request>> groups = new ArrayList<>();

		groups.add(requests);

		for (Modifier modifier : modifiers) {

			List<List<Request>> nextGroups = new ArrayList<>();

			for (List<Request> group : groups) {

				if (group.size() == 1) {
					nextGroups.add(group);
					continue;
				}

				Map<HashCode, List<Request>> partition = new HashMap<>();

				for (Request request : group) {

					HashCode reqHash = this.HashBasedOnModifier(request, modifier);

					partition.computeIfAbsent(reqHash, key -> new ArrayList<>()).add(request);

				}

				nextGroups.addAll(partition.values());
			}

			groups = nextGroups;
		}

		return groups;
	}

	private HashCode HashBasedOnModifier(Request request, Modifier modifier) {

		HashStrategy strategy = resolver.resolve(modifier.getTarget());

		HashCode code = strategy.hashStrategyTarget(request, this.HASHFUNCTION);

		return code;

	}

}
