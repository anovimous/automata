package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.HostRepository;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.TargetSelectionResult;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.common.enums.RequestContentType;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TargetSelectionServiceTest {

	@Mock
	HostRepository hostRepo;
	@Mock
	RequestRepository requestRepo;

	@InjectMocks
	TargetSelectionService service;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static Request request(Long id, RequestContentType contentType, long size) {
		Request request = Request.of(id);
		request.setContentType(contentType);
		request.setBodySizeBytes(size);
		return request;
	}

	private static TargetSelector selector(SelectorType type, String json) throws Exception {
		return new TargetSelector(type, MAPPER.readTree(json));
	}

	@Test
	void singleRequestEligibleIsSelected() throws Exception {
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request(5L, RequestContentType.JSON, 4)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":5}"));

		assertEquals(5L, result.requestId());
	}

	@Test
	void singleBodylessRequestIsSelected() throws Exception {
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request(5L, null, 0)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":5}"));

		assertEquals(5L, result.requestId());
	}

	@Test
	void singleRequestIneligibleIsRejected() throws Exception {
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request(5L, RequestContentType.MULTIPART, 4)));

		assertThrows(IneligibleJobTargetException.class,
				() -> service.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":5}")));
	}

	@Test
	void singleRequestMissingIsNotFound() throws Exception {
		when(requestRepo.findById(5L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":5}")));
	}

	@Test
	void hostNotFoundIsNotRewrapped() throws Exception {
		when(hostRepo.existsById(3L)).thenReturn(false);

		assertThrows(EntityNotFoundException.class,
				() -> service.handleTargetSelector(selector(SelectorType.SINGLE_HOST, "{\"hostId\":3}")));
	}

	@Test
	void malformedSelectorIsRewrappedAsIllegalArgument() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> service
				.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":\"not-a-number\"}")));
	}

}
