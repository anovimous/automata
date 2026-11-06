package com.automata.job.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.automata.host.Host;
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

		if (selector == null || selector.getSelectorType() == null || selector.getSelector() == null)
			throw new IllegalArgumentException("targetSelector with a selectorType and a selector is required");

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

		Set<Long> requestIds = requireIds(selector.getRequestIds(), "requestIds");

		List<Request> requests = requestRepo.findAllById(requestIds);

		requireAllFound(requestIds, requests.stream().map(Request::getId).collect(Collectors.toSet()), "Request");

		requests.forEach(HttpJobUtils::requireJobEligible);

		// the hosts are already loaded together with the requests, so this costs no query
		Set<Long> hostIds = requests.stream().map(request -> request.getHost().getId()).collect(Collectors.toSet());

		return new TargetSelectionResult.MultipleRequests(requestIds, hostIds);

	}

	private TargetSelectionResult handleMultipleHostsSelector(MultipleHostsSelector selector) {

		Set<Long> hostIds = requireIds(selector.getHostIds(), "hostIds");

		List<Host> hosts = hostRepo.findAllById(hostIds);

		requireAllFound(hostIds, hosts.stream().map(Host::getId).collect(Collectors.toSet()), "Host");

		Set<Long> programIds = hosts.stream().map(host -> host.getProgram().getId()).collect(Collectors.toSet());

		return new TargetSelectionResult.MultipleHosts(hostIds, programIds);

	}

	private static Set<Long> requireIds(Set<Long> ids, String field) {

		if (ids == null || ids.isEmpty() || ids.contains(null))
			throw new IllegalArgumentException(field + " must be a non-empty list of ids");

		return ids;

	}

	private static void requireAllFound(Set<Long> wanted, Set<Long> found, String what) {

		Set<Long> missing = new TreeSet<>(wanted);
		missing.removeAll(found);

		if (!missing.isEmpty())
			throw new EntityNotFoundException(what + " not found: " + missing);

	}

}
