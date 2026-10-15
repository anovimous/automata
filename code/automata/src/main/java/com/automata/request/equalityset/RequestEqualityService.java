package com.automata.request.equalityset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.common.dto.RequestsEqualizationDto;
import com.automata.request.comparator.Comparator;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.comparator.Modifier;
import com.automata.request.comparator.hash.HashStrategyResolver;
import com.automata.request.comparator.hash.strategy.HashStrategy;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestEqualityService {

	private final HashFunction HASHFUNCTION = Hashing.murmur3_128();

	private final EqualizationRepository equalizationRepo;

	private final RequestRepository requestRepo;

	private final HostRepository hostRepo;

	private final ComparatorRepository comparatorRepo;

	private final RequestEqualitySetRepository equalitySetRepo;

	private final RequestEqualityRelationRepository requestEqualityRelationRepo;

	private final HashStrategyResolver resolver;

	public List<Equalization> getEqualizations(Long hostId) {

		Host host = hostRepo.findById(hostId).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		return equalizationRepo.findAllByHost(host);

	}

	public void createEqualizationTask(RequestsEqualizationDto dto) {

		Host host = hostRepo.findById(dto.hostId()).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		Comparator comparator = comparatorRepo.findById(dto.comparatorId())
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

		Equalization equalization = Equalization.builder().autoEqualize(dto.autoEqualize()).host(host)
				.comparator(comparator).build();

		equalizationRepo.save(equalization);

	}

	public void updateEqualization(Long equalizationId, Boolean autoEqualize) {

		Equalization equalization = equalizationRepo.findById(equalizationId)
				.orElseThrow(() -> new EntityNotFoundException("Equalization not found"));

		if (autoEqualize != null)
			equalization.setAutoEqualize(autoEqualize);

		equalizationRepo.save(equalization);

	}

	@Transactional
	@Async("equalizationExecutor")
	public void equalizeRequestsIntoEqualityGroups(Long equalizationId) {

		Equalization equalization = equalizationRepo.findById(equalizationId)
				.orElseThrow(() -> new EntityNotFoundException("Equalization not found"));

		Host host = equalization.getHost();

		Comparator comparator = comparatorRepo.findById(equalization.getComparator().getId()).orElse(null);

		List<Modifier> modifiers = comparator.getModifiers().stream()
				.sorted((first, second) -> Long.compare(first.getPriority(), second.getPriority()))
				.collect(Collectors.toList());

		HashSet<Long> requestsIds = requestRepo.findIdsByHost(host);

		List<RequestEqualityRelationSummaryInternalDto> requestEqualitySetPairs = requestEqualityRelationRepo
				.findRequestEqualitySetPairs(equalization.getId());

		Map<Long, Long> equalitySetRepresentativeRequestPair = new LinkedHashMap<>();

		Map<Long, Long> representativeRequestEqualitySetPair = new LinkedHashMap<>();

		requestEqualitySetPairs.forEach((pair) -> {

			requestsIds.remove(pair.requestId());

			equalitySetRepresentativeRequestPair.putIfAbsent(pair.equalitySetId(), pair.requestId());

			representativeRequestEqualitySetPair.putIfAbsent(pair.requestId(), pair.equalitySetId());

		});

		List<RequestEqualitySet> oldSets = new ArrayList<>(
				equalitySetRepo.findAllById(equalitySetRepresentativeRequestPair.keySet()));

		Map<Long, RequestEqualitySet> setIdMap = oldSets.stream()
				.collect(Collectors.toMap(set -> set.getId(), set -> set));

		Collection<Long> representativeRequestsIds = equalitySetRepresentativeRequestPair.values();

		List<Request> representativeRequests = requestRepo.findAllById(representativeRequestsIds);

		// Main Logic:

		List<Request> unEqualizedRequests = requestRepo.findAllById(requestsIds);

		Map<HashCode, Request> requestHashCodes = representativeRequests.stream()
				.collect(Collectors.toMap((req) -> this.computeFullHashCode(req, modifiers), req -> req));

		List<RequestEqualityRelation> toPersistRelations = new ArrayList<>();

		unEqualizedRequests.forEach((req) -> {

			HashCode hash = this.computeFullHashCode(req, modifiers);

			if (requestHashCodes.containsKey(hash)) {

				Request representative = requestHashCodes.get(hash);

				Long equalitySetId = representativeRequestEqualitySetPair.get(representative.getId());

				RequestEqualitySet toAddToSet = setIdMap.get(equalitySetId);

				toAddToSet.setNumberOfRequests(toAddToSet.getNumberOfRequests() + 1);

				RequestEqualityRelation relation = RequestEqualityRelation.builder().request(req).set(toAddToSet)
						.build();

				toPersistRelations.add(relation);

			} else {

				RequestEqualitySet set = RequestEqualitySet.builder()
						.alias(RequestEqualityUtils.computeAlias(req, modifiers)).equalization(equalization)
						.numberOfRequests(1).build();

				RequestEqualitySet persistedSet = equalitySetRepo.save(set);

				requestHashCodes.put(hash, req);

				representativeRequestEqualitySetPair.put(req.getId(), set.getId());

				setIdMap.put(persistedSet.getId(), persistedSet);

				oldSets.add(persistedSet);

				RequestEqualityRelation relation = RequestEqualityRelation.builder().request(req).set(persistedSet)
						.build();

				toPersistRelations.add(relation);

			}

		});

		equalitySetRepo.saveAll(oldSets);

		requestEqualityRelationRepo.saveAll(toPersistRelations);

		equalization.setLastRun(Instant.now());

		equalizationRepo.save(equalization);

	}

	private HashCode computeFullHashCode(Request request, List<Modifier> modifiers) {

		List<HashCode> codes = new ArrayList<>();

		modifiers.forEach((mod) -> {

			codes.add(this.HashBasedOnModifier(request, mod));

		});

		return Hashing.combineUnordered(codes);
	}

	private HashCode HashBasedOnModifier(Request request, Modifier modifier) {

		HashStrategy strategy = resolver.resolve(modifier.getTarget());

		HashCode code = strategy.hashStrategyTarget(request, this.HASHFUNCTION);

		return code;

	}

}
