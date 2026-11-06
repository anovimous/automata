package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.host.Host;
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

	private static final Long HOST_ID = 7L;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static Request request(Long id, RequestContentType contentType, long size) {
		Request request = Request.of(id);
		request.setContentType(contentType);
		request.setBodySizeBytes(size);
		request.setHost(Host.of(HOST_ID));
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

		assertEquals(new TargetSelectionResult.SingleRequest(5L, HOST_ID), result);
	}

	@Test
	void singleRequestResultCarriesTheHostOfTheRequest() throws Exception {
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request(5L, RequestContentType.JSON, 4)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":5}"));

		assertEquals(HOST_ID, ((TargetSelectionResult.SingleRequest) result).hostId());
	}

	@Test
	void singleHostResultCarriesTheHost() throws Exception {
		when(hostRepo.existsById(3L)).thenReturn(true);

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.SINGLE_HOST, "{\"hostId\":3}"));

		assertEquals(new TargetSelectionResult.SingleHost(3L), result);
	}

	private static Host hostOfProgram(Long hostId, Long programId) {
		Host host = Host.of(hostId);
		com.automata.program.Program program = new com.automata.program.Program();
		program.setId(programId);
		host.setProgram(program);
		return host;
	}

	private static Request requestOfHost(Long id, Long hostId) {
		Request request = request(id, RequestContentType.JSON, 4);
		request.setHost(Host.of(hostId));
		return request;
	}

	@Test
	void multipleHostsCarryTheirIdsAndPrograms() throws Exception {
		when(hostRepo.findAllById(java.util.Set.of(1L, 2L)))
				.thenReturn(List.of(hostOfProgram(1L, 10L), hostOfProgram(2L, 11L)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_HOSTS, "{\"hostIds\":[1,2]}"));

		assertEquals(new TargetSelectionResult.MultipleHosts(java.util.Set.of(1L, 2L), java.util.Set.of(10L, 11L)),
				result);
	}

	@Test
	void multipleHostsWithAnUnknownIdAreNotFoundAndNameTheMissingOnes() throws Exception {
		when(hostRepo.findAllById(java.util.Set.of(1L, 2L, 3L)))
				.thenReturn(List.of(hostOfProgram(1L, 10L)));

		EntityNotFoundException e = assertThrows(EntityNotFoundException.class, () -> service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_HOSTS, "{\"hostIds\":[1,2,3]}")));

		assertEquals("Host not found: [2, 3]", e.getMessage());
	}

	@Test
	void multipleHostsWithoutIdsAreRefused() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> service.handleTargetSelector(selector(SelectorType.MULTIPLE_HOSTS, "{\"hostIds\":[]}")));
		assertThrows(IllegalArgumentException.class,
				() -> service.handleTargetSelector(selector(SelectorType.MULTIPLE_HOSTS, "{}")));
		assertThrows(IllegalArgumentException.class,
				() -> service.handleTargetSelector(selector(SelectorType.MULTIPLE_HOSTS, "{\"hostIds\":[1,null]}")));
	}

	@Test
	void multipleRequestsCarryTheirIdsAndHosts() throws Exception {
		when(requestRepo.findAllById(java.util.Set.of(5L, 6L)))
				.thenReturn(List.of(requestOfHost(5L, 7L), requestOfHost(6L, 8L)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_REQUESTS, "{\"requestIds\":[5,6]}"));

		assertEquals(new TargetSelectionResult.MultipleRequests(java.util.Set.of(5L, 6L), java.util.Set.of(7L, 8L)),
				result);
	}

	@Test
	void multipleRequestsOfOneHostCarryThatHostOnce() throws Exception {
		when(requestRepo.findAllById(java.util.Set.of(5L, 6L)))
				.thenReturn(List.of(requestOfHost(5L, 7L), requestOfHost(6L, 7L)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_REQUESTS, "{\"requestIds\":[5,6]}"));

		assertEquals(java.util.Set.of(7L), ((TargetSelectionResult.MultipleRequests) result).hostsIds());
	}

	@Test
	void multipleRequestsWithAnUnknownIdAreNotFoundAndNameTheMissingOnes() throws Exception {
		when(requestRepo.findAllById(java.util.Set.of(5L, 6L))).thenReturn(List.of(requestOfHost(5L, 7L)));

		EntityNotFoundException e = assertThrows(EntityNotFoundException.class, () -> service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_REQUESTS, "{\"requestIds\":[5,6]}")));

		assertEquals("Request not found: [6]", e.getMessage());
	}

	@Test
	void multipleRequestsWithAnIneligibleOneAreRefused() throws Exception {
		Request multipart = requestOfHost(6L, 7L);
		multipart.setContentType(RequestContentType.MULTIPART);
		when(requestRepo.findAllById(java.util.Set.of(5L, 6L))).thenReturn(List.of(requestOfHost(5L, 7L), multipart));

		assertThrows(IneligibleJobTargetException.class, () -> service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_REQUESTS, "{\"requestIds\":[5,6]}")));
	}

	@Test
	void multipleRequestsWithoutIdsAreRefused() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> service
				.handleTargetSelector(selector(SelectorType.MULTIPLE_REQUESTS, "{\"requestIds\":[]}")));
		assertThrows(IllegalArgumentException.class,
				() -> service.handleTargetSelector(selector(SelectorType.MULTIPLE_REQUESTS, "{}")));
	}

	@Test
	void singleBodylessRequestIsSelected() throws Exception {
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request(5L, null, 0)));

		TargetSelectionResult result = service
				.handleTargetSelector(selector(SelectorType.SINGLE_REQUEST, "{\"requestId\":5}"));

		assertEquals(new TargetSelectionResult.SingleRequest(5L, HOST_ID), result);
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
