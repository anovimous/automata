package com.automata.job.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.HostRepository;
import com.automata.job.domain.model.embedded.MultipleEqualitySetsSelector;
import com.automata.job.domain.model.embedded.MultipleHostsSelector;
import com.automata.job.domain.model.embedded.MultipleRequestsSelector;
import com.automata.job.domain.model.embedded.SingleEqualitySetSelector;
import com.automata.job.domain.model.embedded.SingleHostSelector;
import com.automata.job.domain.model.embedded.SingleRequestSelector;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.job.utils.HttpJobUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TargetSelectionService {

	private final HostRepository hostRepo;

	private final RequestRepository requestRepo;

	private final RequestEqualitySetRepository equalitySetRepo;

	private final RequestEqualityRelationRepository requestEqualityRelationRepo;

	public TargetSelectionResult handleTargetSelector(TargetSelector selector) {

		ObjectMapper mapper = new ObjectMapper();

		TargetSelectionResult result;

		try {
			result = switch (selector.getSelectorType()) {
			case SelectorType.SINGLE_HOST ->
				this.handleSingleHostSelector(mapper.treeToValue(selector.getSelector(), SingleHostSelector.class));
			case SelectorType.SINGLE_REQUEST -> this.handleSingleRequestSelector(
					mapper.treeToValue(selector.getSelector(), SingleRequestSelector.class));
			case SelectorType.SINGLE_EQUALITY_SET -> this.handleSingleEqualitySetSelector(
					mapper.treeToValue(selector.getSelector(), SingleEqualitySetSelector.class));
			case SelectorType.MULTIPLE_HOSTS -> this.handleMultipleHostsSelector(
					mapper.treeToValue(selector.getSelector(), MultipleHostsSelector.class));
			case SelectorType.MULTIPLE_REQUESTS -> this.handleMultipleRequestsSelector(
					mapper.treeToValue(selector.getSelector(), MultipleRequestsSelector.class));
			case SelectorType.MULTIPLE_EQUALITY_SETS -> this.handleMultipleEqualitySetsSelector(
					mapper.treeToValue(selector.getSelector(), MultipleEqualitySetsSelector.class));
			default -> throw new IllegalArgumentException("Illegal selector");
			};
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException(e.getMessage());
		}

		return result;

	}

	private TargetSelectionResult handleSingleHostSelector(SingleHostSelector selector) {

		Long hostId = selector.getHostId();

		boolean exists = hostRepo.existsById(hostId);

		if (exists)
			return TargetSelectionResult.builder().hostId(hostId).build();
		else
			throw new EntityNotFoundException("Host not found based on the given selector");

	}

	private TargetSelectionResult handleSingleRequestSelector(SingleRequestSelector selector) {

		Long requestId = selector.getRequestId();

		Request request = requestRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request can't be found based on the given selector"));

		HttpJobUtils.requireJobEligible(request);

		return TargetSelectionResult.builder().requestId(requestId).build();

	}

	@Transactional
	private TargetSelectionResult handleSingleEqualitySetSelector(SingleEqualitySetSelector selector) {

		Long equalitySetId = selector.getEqualitySetId();

		boolean exists = equalitySetRepo.existsById(equalitySetId);

		if (!exists)
			throw new EntityNotFoundException("EqualitySet can't be found based on the given selector");

		Request req = requestEqualityRelationRepo
				.findAllInSet(equalitySetRepo.getReferenceById(equalitySetId),
						PageRequest.of(0, 1, Sort.by("insertionDate").descending()))
				.stream().findFirst()
				.orElseThrow(() -> new RuntimeException("Illegal equality set, no requests attached to it"));

		HttpJobUtils.requireJobEligible(req);

		return TargetSelectionResult.builder().requestId(req.getId()).build();

	}

	private TargetSelectionResult handleMultipleEqualitySetsSelector(MultipleEqualitySetsSelector selector) {
//TODO
		throw new RuntimeException("Not implemented yet");

	}

	private TargetSelectionResult handleMultipleRequestsSelector(MultipleRequestsSelector selector) {
//TODO
		throw new RuntimeException("Not implemented yet");
	}

	private TargetSelectionResult handleMultipleHostsSelector(MultipleHostsSelector selector) {
//TODO
		throw new RuntimeException("Not implemented yet");
	}

}
