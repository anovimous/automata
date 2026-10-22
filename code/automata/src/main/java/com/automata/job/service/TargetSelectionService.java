package com.automata.job.service;

import org.springframework.stereotype.Service;

import com.automata.host.HostRepository;
import com.automata.job.domain.model.embedded.MultipleHostsSelector;
import com.automata.job.domain.model.embedded.MultipleRequestsSelector;
import com.automata.job.domain.model.embedded.SingleHostSelector;
import com.automata.job.domain.model.embedded.SingleRequestSelector;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
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

	public TargetSelectionResult handleTargetSelector(TargetSelector selector) {

		ObjectMapper mapper = new ObjectMapper();

		TargetSelectionResult result;

		try {
			result = switch (selector.getSelectorType()) {
			case SelectorType.SINGLE_HOST ->
				this.handleSingleHostSelector(mapper.treeToValue(selector.getSelector(), SingleHostSelector.class));
			case SelectorType.SINGLE_REQUEST -> this.handleSingleRequestSelector(
					mapper.treeToValue(selector.getSelector(), SingleRequestSelector.class));
			case SelectorType.MULTIPLE_HOSTS -> this.handleMultipleHostsSelector(
					mapper.treeToValue(selector.getSelector(), MultipleHostsSelector.class));
			case SelectorType.MULTIPLE_REQUESTS -> this.handleMultipleRequestsSelector(
					mapper.treeToValue(selector.getSelector(), MultipleRequestsSelector.class));
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
			return new TargetSelectionResult.SingleHost(hostId);
		else
			throw new EntityNotFoundException("Host not found based on the given selector");

	}

	private TargetSelectionResult handleSingleRequestSelector(SingleRequestSelector selector) {

		Long requestId = selector.getRequestId();

		Request request = requestRepo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("Request can't be found based on the given selector"));

		HttpJobUtils.requireJobEligible(request);

		// the host is already loaded together with the request, so this costs no query
		return new TargetSelectionResult.SingleRequest(requestId, request.getHost().getId());

	}

	private TargetSelectionResult handleMultipleRequestsSelector(MultipleRequestsSelector selector) {
//TODO
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private TargetSelectionResult handleMultipleHostsSelector(MultipleHostsSelector selector) {
//TODO
		throw new UnsupportedOperationException("Not implemented yet");
	}

}
