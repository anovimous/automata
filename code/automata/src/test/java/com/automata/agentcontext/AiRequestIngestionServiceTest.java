package com.automata.agentcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.agentcontext.common.dto.AiRequestAdditionDto;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.Request;
import com.automata.request.RequestService;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.enums.Source;
import com.automata.tenant.Tenant;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AiRequestIngestionServiceTest {

	private static final UUID CONTEXT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private HostRepository hostRepo;
	@Mock
	private AgentContextRepository contextRepo;
	@Mock
	private RequestService requestService;

	@InjectMocks
	private AiRequestIngestionService service;

	private Host host(Long id) {
		Host host = new Host();
		host.setId(id);
		return host;
	}

	private Tenant tenant(Long id, Host host) {
		return Tenant.builder().id(id).name("t").email("t@a.com").host(host).build();
	}

	private RawRequestAdditionDto added() {
		ArgumentCaptor<RawRequestAdditionDto> captor = ArgumentCaptor.forClass(RawRequestAdditionDto.class);
		verify(requestService).addRawRequestWithResponse(captor.capture());
		return captor.getValue();
	}

	@Test
	void addRawRequest_withoutATenant_isStoredWithTheAiSourceAndNoTenant() {
		Request created = Request.of(9L);
		when(hostRepo.findByHost("api.example.com")).thenReturn(Optional.of(host(3L)));
		when(requestService.addRawRequestWithResponse(any())).thenReturn(created);

		Request result = service.addRawRequest(
				new AiRequestAdditionDto("cmVx", "api.example.com", "cmVzcA==", null, null));

		assertSame(created, result);
		RawRequestAdditionDto dto = added();
		assertEquals("cmVx", dto.requestBase64());
		assertEquals("cmVzcA==", dto.responseBase64());
		assertEquals(Source.AI, dto.source());
		assertEquals(3L, dto.hostId());
		assertNull(dto.tenantId());
		verifyNoInteractions(contextRepo);
	}

	@Test
	void addRawRequest_withoutAResponse_passesNullOn() {
		when(hostRepo.findByHost("a.com")).thenReturn(Optional.of(host(3L)));

		service.addRawRequest(new AiRequestAdditionDto("cmVx", "a.com", null, null, null));

		assertNull(added().responseBase64());
	}

	@Test
	void addRawRequest_aContextWithoutATenant_isIgnored() {
		when(hostRepo.findByHost("a.com")).thenReturn(Optional.of(host(3L)));

		service.addRawRequest(new AiRequestAdditionDto("cmVx", "a.com", null, null, CONTEXT_UUID));

		verifyNoInteractions(contextRepo);
		assertNull(added().tenantId());
	}

	@Test
	void addRawRequest_unknownHost_isNotFound() {
		when(hostRepo.findByHost("nope.com")).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.addRawRequest(new AiRequestAdditionDto("cmVx", "nope.com", null, null, null)));

		verifyNoInteractions(requestService);
	}

	@Test
	void addRawRequest_tenantOfTheContext_isStoredOnTheRequest() {
		Host host = host(3L);
		AgentContext context = AgentContext.builder().id(1L).host(host).tenants(List.of(tenant(4L, host))).build();
		when(hostRepo.findByHost("a.com")).thenReturn(Optional.of(host));
		when(contextRepo.findByUuid(CONTEXT_UUID)).thenReturn(Optional.of(context));

		service.addRawRequest(new AiRequestAdditionDto("cmVx", "a.com", null, 4L, CONTEXT_UUID));

		RawRequestAdditionDto dto = added();
		assertEquals(4L, dto.tenantId());
		assertEquals(Source.AI, dto.source());
	}

	@Test
	void addRawRequest_tenantWithoutAContext_isRejected() {
		when(hostRepo.findByHost("a.com")).thenReturn(Optional.of(host(3L)));

		assertThrows(IllegalArgumentException.class,
				() -> service.addRawRequest(new AiRequestAdditionDto("cmVx", "a.com", null, 4L, null)));

		verify(requestService, never()).addRawRequestWithResponse(any());
	}

	@Test
	void addRawRequest_unknownContext_isNotFound() {
		when(hostRepo.findByHost("a.com")).thenReturn(Optional.of(host(3L)));
		when(contextRepo.findByUuid(CONTEXT_UUID)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.addRawRequest(new AiRequestAdditionDto("cmVx", "a.com", null, 4L, CONTEXT_UUID)));

		verify(requestService, never()).addRawRequestWithResponse(any());
	}

	@Test
	void addRawRequest_tenantNotInTheContext_isRejected() {
		Host host = host(3L);
		AgentContext context = AgentContext.builder().id(1L).host(host).tenants(List.of(tenant(5L, host))).build();
		when(hostRepo.findByHost("a.com")).thenReturn(Optional.of(host));
		when(contextRepo.findByUuid(CONTEXT_UUID)).thenReturn(Optional.of(context));

		assertThrows(IllegalArgumentException.class,
				() -> service.addRawRequest(new AiRequestAdditionDto("cmVx", "a.com", null, 4L, CONTEXT_UUID)));

		verify(requestService, never()).addRawRequestWithResponse(any());
	}

	@Test
	void addRawRequest_tenantOfAnotherHostThanTheRequest_isRejected() {
		Host contextHost = host(3L);
		AgentContext context = AgentContext.builder().id(1L).host(contextHost)
				.tenants(List.of(tenant(4L, contextHost))).build();
		when(hostRepo.findByHost("other.com")).thenReturn(Optional.of(host(8L)));
		when(contextRepo.findByUuid(CONTEXT_UUID)).thenReturn(Optional.of(context));

		assertThrows(IllegalArgumentException.class,
				() -> service.addRawRequest(new AiRequestAdditionDto("cmVx", "other.com", null, 4L, CONTEXT_UUID)));

		verify(requestService, never()).addRawRequestWithResponse(any());
	}

}
