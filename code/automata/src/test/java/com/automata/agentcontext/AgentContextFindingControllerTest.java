package com.automata.agentcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.GlobalExceptionHandler;
import com.automata.agentcontext.common.dto.AiFindingCreationRequest;
import com.automata.finding.Finding;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.host.Host;
import com.automata.request.Request;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AgentContextFindingControllerTest {

	private static final UUID CONTEXT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final String BASE = "/api/agent-contexts/uuid/" + CONTEXT_UUID + "/findings";

	@Mock
	private AgentContextFindingService findingService;

	@InjectMocks
	private AgentContextFindingController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
	}

	private Finding finding(Long id) {
		Host host = new Host();
		host.setId(2L);
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setId(4L);
		AgentContext own = AgentContext.builder().id(1L).uuid(CONTEXT_UUID).build();
		return Finding.builder().id(id).request(Request.of(10L)).host(host).vulnerability(vulnerability)
				.severity(7.5).confidence(8.0).status(FindingStatus.SUGGESTED).evidence("evidence")
				.createdBy(FindingCreator.AI).agentContext(own).humanVerdict(HumanVerdict.TRUE_POSITIVE).build();
	}

	// create

	@Test
	void create_answers201WithTheScopedLocation() throws Exception {
		when(findingService.createFinding(eq(CONTEXT_UUID), any())).thenReturn(finding(3L));

		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(
				"{\"requestId\":10,\"vulnerabilityId\":5,\"severity\":7.5,\"status\":\"SUGGESTED\",\"confidence\":8.0,\"evidence\":\"e\"}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/agent-contexts/uuid/" + CONTEXT_UUID + "/findings/3"));

		ArgumentCaptor<AiFindingCreationRequest> body = ArgumentCaptor.forClass(AiFindingCreationRequest.class);
		verify(findingService).createFinding(eq(CONTEXT_UUID), body.capture());
		assertEquals(10L, body.getValue().requestId());
		assertEquals(FindingStatus.SUGGESTED, body.getValue().status());
	}

	@Test
	void create_requestOutsideTheContext_is404() throws Exception {
		when(findingService.createFinding(eq(CONTEXT_UUID), any())).thenThrow(new EntityNotFoundException("Request not found"));

		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":10}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void create_missingField_is400() throws Exception {
		when(findingService.createFinding(eq(CONTEXT_UUID), any()))
				.thenThrow(new IllegalArgumentException("evidence is required"));

		mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":10}"))
				.andExpect(status().isBadRequest());
	}

	// list

	@Test
	void list_returnsTheFindingsWithTheirOriginAndNoContextId() throws Exception {
		Pageable pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(
				org.springframework.data.domain.Sort.Direction.DESC, "creationDate"));
		when(findingService.getFindings(CONTEXT_UUID, null, null, null, null, null, pageable))
				.thenReturn(new PageImpl<>(List.of(finding(3L)), pageable, 1));

		mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(3))
				.andExpect(jsonPath("$.content[0].origin").value("THIS_CONTEXT"))
				.andExpect(jsonPath("$.content[0].humanVerdict").value("TRUE_POSITIVE"))
				.andExpect(jsonPath("$.content[0].agentContext").doesNotExist())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void list_passesTheFilters() throws Exception {
		when(findingService.getFindings(eq(CONTEXT_UUID), eq(10L), eq(8L), eq(FindingStatus.CONFIRMED),
				eq(HumanVerdict.FALSE_POSITIVE), eq(FindingCreator.USER), any()))
				.thenReturn(new PageImpl<>(List.of()));

		mvc.perform(get(BASE).param("requestId", "10").param("jobId", "8").param("status", "CONFIRMED")
				.param("humanVerdict", "FALSE_POSITIVE").param("createdBy", "USER")).andExpect(status().isOk());
	}

	@Test
	void list_capsThePageSizeAt100() throws Exception {
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		when(findingService.getFindings(eq(CONTEXT_UUID), any(), any(), any(), any(), any(), pageable.capture()))
				.thenReturn(new PageImpl<>(List.of()));

		mvc.perform(get(BASE).param("size", "500").param("page", "2")).andExpect(status().isOk());

		assertEquals(100, pageable.getValue().getPageSize());
		assertEquals(2, pageable.getValue().getPageNumber());
	}

	// get

	@Test
	void get_returnsTheDetailWithoutAContextId() throws Exception {
		when(findingService.getFinding(CONTEXT_UUID, 3L)).thenReturn(finding(3L));

		mvc.perform(get(BASE + "/3")).andExpect(status().isOk()).andExpect(jsonPath("$.evidence").value("evidence"))
				.andExpect(jsonPath("$.origin").value("THIS_CONTEXT"))
				.andExpect(jsonPath("$.agentContext").doesNotExist());
	}

	@Test
	void get_invisibleFinding_is404() throws Exception {
		when(findingService.getFinding(CONTEXT_UUID, 3L)).thenThrow(new EntityNotFoundException("Finding not found"));

		mvc.perform(get(BASE + "/3")).andExpect(status().isNotFound());
	}

	// patch

	@Test
	void patch_answers204() throws Exception {
		mvc.perform(patch(BASE + "/3").contentType(MediaType.APPLICATION_JSON).content("{\"severity\":6.0}"))
				.andExpect(status().isNoContent());

		verify(findingService).patchFinding(eq(CONTEXT_UUID), eq(3L), any());
	}

	@Test
	void patch_reviewedOrForeignFinding_is409() throws Exception {
		doThrow(new InvalidFindingStateException("reviewed")).when(findingService).patchFinding(eq(CONTEXT_UUID),
				eq(3L), any());

		mvc.perform(patch(BASE + "/3").contentType(MediaType.APPLICATION_JSON).content("{\"severity\":6.0}"))
				.andExpect(status().isConflict());
	}

	@Test
	void patch_invisibleFinding_is404() throws Exception {
		doThrow(new EntityNotFoundException("Finding not found")).when(findingService).patchFinding(eq(CONTEXT_UUID),
				eq(3L), any());

		mvc.perform(patch(BASE + "/3").contentType(MediaType.APPLICATION_JSON).content("{\"severity\":6.0}"))
				.andExpect(status().isNotFound());
	}

}
