package com.automata.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingFilter;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.dto.FindingReviewRequest;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.host.Host;
import com.automata.request.Request;
import com.automata.testsupport.Mvc;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class FindingControllerTest {

	@Mock
	FindingService findingService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new FindingController(findingService)).build();
	}

	private static Finding finding(long id) {
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setId(3L);
		return Finding.builder().id(id).request(Request.of(4L)).host(Host.of(5L)).vulnerability(vulnerability).severity(7.5)
				.confidence(8).status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).build();
	}

	@Test
	void createAnswers201WithLocationAndPassesTheRequest() throws Exception {
		when(findingService.createFinding(any(FindingCreationRequest.class))).thenReturn(finding(9));

		mvc.perform(post("/api/findings").contentType(MediaType.APPLICATION_JSON).content(
				"{\"requestId\":4,\"vulnerabilityId\":3,\"severity\":7.5,\"status\":\"SUGGESTED\",\"confidence\":8,\"evidence\":\"e\",\"createdBy\":\"USER\"}"))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/findings/9"));

		ArgumentCaptor<FindingCreationRequest> request = ArgumentCaptor.forClass(FindingCreationRequest.class);
		verify(findingService).createFinding(request.capture());
		assertEquals(4L, request.getValue().requestId());
		assertEquals(7.5, request.getValue().severity());
		assertEquals(FindingCreator.USER, request.getValue().createdBy());
	}

	@Test
	void createRejectedByTheServiceAnswers400() throws Exception {
		when(findingService.createFinding(any(FindingCreationRequest.class)))
				.thenThrow(new IllegalArgumentException("Severity must be between 0.0 and 10.0"));

		mvc.perform(post("/api/findings").contentType(MediaType.APPLICATION_JSON).content("{\"severity\":11}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createWithAnUnknownRequestAnswers404() throws Exception {
		when(findingService.createFinding(any(FindingCreationRequest.class))).thenThrow(new EntityNotFoundException("Request not found"));

		mvc.perform(post("/api/findings").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":99}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void getReturnsTheDetails() throws Exception {
		when(findingService.getFinding(9L)).thenReturn(finding(9));

		mvc.perform(get("/api/findings/9")).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(4))
				.andExpect(jsonPath("$.hostId").value(5)).andExpect(jsonPath("$.vulnerabilityId").value(3))
				.andExpect(jsonPath("$.evidence").value("e")).andExpect(jsonPath("$.jobId").doesNotExist());
	}

	@Test
	void getOfUnknownFindingAnswers404() throws Exception {
		when(findingService.getFinding(99L)).thenThrow(new EntityNotFoundException("Finding not found"));

		mvc.perform(get("/api/findings/99")).andExpect(status().isNotFound());
	}

	@Test
	void listBindsEveryFilter() throws Exception {
		when(findingService.getFindings(any(FindingFilter.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(finding(1))));

		mvc.perform(get("/api/findings").param("hostId", "5").param("requestId", "4").param("jobId", "2").param("status", "CONFIRMED")
				.param("humanVerdict", "TRUE_POSITIVE").param("createdBy", "AI")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1));

		ArgumentCaptor<FindingFilter> filter = ArgumentCaptor.forClass(FindingFilter.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(findingService).getFindings(filter.capture(), pageable.capture());
		assertEquals(new FindingFilter(5L, 4L, 2L, FindingStatus.CONFIRMED, HumanVerdict.TRUE_POSITIVE, FindingCreator.AI), filter.getValue());
		assertEquals(20, pageable.getValue().getPageSize());
		assertEquals("creationDate: DESC", pageable.getValue().getSort().toString());
	}

	@Test
	void listWithoutFiltersPassesNulls() throws Exception {
		when(findingService.getFindings(any(FindingFilter.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

		mvc.perform(get("/api/findings")).andExpect(status().isOk());

		ArgumentCaptor<FindingFilter> filter = ArgumentCaptor.forClass(FindingFilter.class);
		verify(findingService).getFindings(filter.capture(), any(Pageable.class));
		assertEquals(new FindingFilter(null, null, null, null, null, null), filter.getValue());
	}

	@Test
	void patchAnswers204AndPassesTheOptionals() throws Exception {
		mvc.perform(patch("/api/findings/9").contentType(MediaType.APPLICATION_JSON)
				.content("{\"severity\":3.5,\"setAiDescriptionNull\":true}")).andExpect(status().isNoContent());

		ArgumentCaptor<FindingPatchRequest> request = ArgumentCaptor.forClass(FindingPatchRequest.class);
		verify(findingService).patchFinding(eq(9L), request.capture());
		assertEquals(3.5, request.getValue().severity().orElseThrow());
		assertTrue(request.getValue().status().isEmpty());
		assertTrue(request.getValue().setAiDescriptionNull());
	}

	@Test
	void reviewAnswers204() throws Exception {
		mvc.perform(post("/api/findings/9/review").contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\":\"FALSE_POSITIVE\",\"comment\":\"not exploitable\"}")).andExpect(status().isNoContent());

		verify(findingService).reviewFinding(9L, new FindingReviewRequest(HumanVerdict.FALSE_POSITIVE, "not exploitable"));
	}

	@Test
	void reviewWithAnUnknownVerdictAnswers400() throws Exception {
		mvc.perform(post("/api/findings/9/review").contentType(MediaType.APPLICATION_JSON).content("{\"verdict\":\"MAYBE\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void patchOfAFindingInAnInvalidStateAnswers409() throws Exception {
		doThrow(new InvalidFindingStateException("reviewed")).when(findingService).patchFinding(eq(9L), any(FindingPatchRequest.class));

		mvc.perform(patch("/api/findings/9").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
	}

	@Test
	void deleteAnswers204() throws Exception {
		mvc.perform(delete("/api/findings/9")).andExpect(status().isNoContent());

		verify(findingService).deleteFinding(9L);
	}

	@Test
	void deleteOfUnknownFindingAnswers404() throws Exception {
		doThrow(new EntityNotFoundException("Finding not found")).when(findingService).deleteFinding(99L);

		mvc.perform(delete("/api/findings/99")).andExpect(status().isNotFound());
	}
}
