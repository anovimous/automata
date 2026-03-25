package com.automata.job;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.automata.job.selector.TargetSelector;
import com.automata.job.selector.result.TargetSelectionResult;
import com.automata.job.selector.MultipleEqualitySetsSelector;
import com.automata.job.selector.MultipleHostsSelector;
import com.automata.job.selector.MultipleRequestsSelector;
import com.automata.job.selector.SingleEqualitySetSelector;
import com.automata.job.selector.SingleHostSelector;
import com.automata.job.selector.SingleRequestSelector;
import com.automata.host.HostRepository;
import com.automata.job.common.enums.SelectorType;
import com.automata.request.RequestRepository;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TargetSelectionService {

	private final HostRepository hostRepo;

	private final RequestRepository requestRepo;

	private final RequestEqualitySetRepository equalitySetRepo;

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
		} catch (Exception e) {
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

		boolean exists = requestRepo.existsById(requestId);

		if (exists)
			return TargetSelectionResult.builder().requestId(requestId).build();
		else
			throw new EntityNotFoundException("Request can't be found based on the given selector");

	}

	private TargetSelectionResult handleSingleEqualitySetSelector(SingleEqualitySetSelector selector) {

		Long equalitySetId = selector.getEqualitySetId();

		boolean exists = equalitySetRepo.existsById(equalitySetId);

		if (!exists)
			throw new EntityNotFoundException("EqualitySet can't be found based on the given selector");

		Long requestId = requestRepo.findRequestIdsPageByEqualitySetId(equalitySetId, PageRequest.of(0, 1)).stream()
				.findFirst()
				.orElseThrow(() -> new RuntimeException("Illegal equality set, no requests attached to it"));

		return TargetSelectionResult.builder().requestId(requestId).build();

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
