package com.automata.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.enums.Source;
import com.automata.testsupport.KnownBug;
import com.automata.testsupport.Mvc;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class BurpAdapterTest {

	@Mock
	HostRepository hostRepo;

	@Mock
	RequestService requestService;

	@InjectMocks
	BurpAdapterService adapter;

	@Test
	void theRequestIsAddedToTheHostOfTheGivenNameWithTheBurpSource() {
		when(hostRepo.findByHost("api.example.com")).thenReturn(Optional.of(Host.of(4L)));

		adapter.addRawRequest("cmVx", "api.example.com", "cmVz");

		ArgumentCaptor<RawRequestAdditionDto> dto = ArgumentCaptor.forClass(RawRequestAdditionDto.class);
		verify(requestService).addRawRequestWithResponse(dto.capture());
		assertEquals(new RawRequestAdditionDto("cmVx", Source.BURP, 4L, null, "cmVz"), dto.getValue());
		assertNull(dto.getValue().tenantId());
	}

	@Test
	void aMissingResponseIsPassedAsNull() {
		when(hostRepo.findByHost("api.example.com")).thenReturn(Optional.of(Host.of(4L)));

		adapter.addRawRequest("cmVx", "api.example.com", null);

		ArgumentCaptor<RawRequestAdditionDto> dto = ArgumentCaptor.forClass(RawRequestAdditionDto.class);
		verify(requestService).addRawRequestWithResponse(dto.capture());
		assertNull(dto.getValue().responseBase64());
	}

	@Test
	void anUnknownHostIsNotFoundAndNothingIsStored() {
		when(hostRepo.findByHost("nope.example.com")).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> adapter.addRawRequest("cmVx", "nope.example.com", null));
		verify(requestService, never()).addRawRequestWithResponse(any());
	}

	@Test
	@KnownBug("BUG-044")
	void theHostNameIsMatchedIgnoringCase() {
		// hosts are stored lower-cased, a Burp host header may not be
		when(hostRepo.findByHost("API.Example.com")).thenReturn(Optional.empty());
		when(hostRepo.findByHost("api.example.com")).thenReturn(Optional.of(Host.of(4L)));

		adapter.addRawRequest("cmVx", "API.Example.com", null);

		verify(requestService).addRawRequestWithResponse(any());
	}

	@Test
	void theBurpRouteAnswers201() throws Exception {
		MockMvc mvc = Mvc.standalone(new RequestBurpIntegrationController(adapter)).build();
		when(hostRepo.findByHost("api.example.com")).thenReturn(Optional.of(Host.of(4L)));

		mvc.perform(post("/api/external/requests").contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestBase64\":\"cmVx\",\"host\":\"api.example.com\",\"base64Response\":\"cmVz\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void theBurpRouteAnswers404ForAnUnknownHost() throws Exception {
		MockMvc mvc = Mvc.standalone(new RequestBurpIntegrationController(adapter)).build();
		when(hostRepo.findByHost("nope")).thenReturn(Optional.empty());

		mvc.perform(post("/api/external/requests").contentType(MediaType.APPLICATION_JSON)
				.content("{\"requestBase64\":\"cmVx\",\"host\":\"nope\"}")).andExpect(status().isNotFound());
	}
}
