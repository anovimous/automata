package com.automata.agentcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.automata.agentcontext.common.dto.AiFindingCreationRequest;
import com.automata.finding.Finding;
import com.automata.finding.FindingService;
import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.job.service.HttpJobQueryService;
import com.automata.request.Request;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AgentContextFindingServiceTest {

	private static final UUID UUID_OF_CONTEXT = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	AgentContextService contextService;
	@Mock
	FindingService findingService;
	@Mock
	HttpJobQueryService jobQueryService;

	@InjectMocks
	AgentContextFindingService service;

	private final AgentContext context = AgentContext.builder().id(1L).uuid(UUID_OF_CONTEXT).build();

	private AiFindingCreationRequest creation(Long jobId) {
		return new AiFindingCreationRequest(10L, jobId, 5L, 7.5, FindingStatus.SUGGESTED, 8.0, "evidence", null,
				"description");
	}

	private Finding finding(FindingCreator createdBy, AgentContext findingContext, HumanVerdict verdict) {
		return Finding.builder().id(3L).request(Request.of(10L)).createdBy(createdBy).agentContext(findingContext)
				.humanVerdict(verdict).build();
	}

	private void contextFound() {
		when(contextService.getContextByUuid(UUID_OF_CONTEXT)).thenReturn(context);
	}

	// ---- createFinding

	@Test
	void createFindingForcesTheAiCreatorAndLinksTheContext() {
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(true);
		Finding created = Finding.builder().id(3L).build();
		when(findingService.createFinding(any(), eq(context))).thenReturn(created);

		Finding result = service.createFinding(UUID_OF_CONTEXT, creation(null));

		assertSame(created, result);
		ArgumentCaptor<FindingCreationRequest> captor = ArgumentCaptor.forClass(FindingCreationRequest.class);
		verify(findingService).createFinding(captor.capture(), eq(context));
		assertEquals(FindingCreator.AI, captor.getValue().createdBy());
		assertEquals(10L, captor.getValue().requestId());
		assertEquals(7.5, captor.getValue().severity());
		assertEquals("description", captor.getValue().aiDescription());
		verify(jobQueryService, never()).getJobOfContext(any(), any());
	}

	@Test
	void createFindingOnARequestOutsideTheContextIsNotFound() {
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(false);

		assertThrows(EntityNotFoundException.class, () -> service.createFinding(UUID_OF_CONTEXT, creation(null)));
		verify(findingService, never()).createFinding(any(), any());
	}

	@Test
	void createFindingWithAJobChecksTheJobIsAnAiJobOfTheContextThatTargetedTheRequest() {
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(true);
		when(findingService.createFinding(any(), eq(context))).thenReturn(Finding.builder().id(3L).build());

		service.createFinding(UUID_OF_CONTEXT, creation(8L));

		verify(jobQueryService).getJobOfContext(UUID_OF_CONTEXT, 8L);
		verify(jobQueryService).getJobOfRequest(10L, 8L);
	}

	@Test
	void createFindingWithAJobOfAnotherContextIsNotFound() {
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(true);
		when(jobQueryService.getJobOfContext(UUID_OF_CONTEXT, 8L)).thenThrow(new EntityNotFoundException("Job not found"));

		assertThrows(EntityNotFoundException.class, () -> service.createFinding(UUID_OF_CONTEXT, creation(8L)));
		verify(findingService, never()).createFinding(any(), any());
	}

	@Test
	void createFindingWithAJobThatDidNotTargetTheRequestIsNotFound() {
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(true);
		when(jobQueryService.getJobOfRequest(10L, 8L)).thenThrow(new EntityNotFoundException("Job not found"));

		assertThrows(EntityNotFoundException.class, () -> service.createFinding(UUID_OF_CONTEXT, creation(8L)));
		verify(findingService, never()).createFinding(any(), any());
	}

	@Test
	void createFindingRequiresTheMandatoryFields() {
		AiFindingCreationRequest noRequest = new AiFindingCreationRequest(null, null, 5L, 1.0,
				FindingStatus.SUGGESTED, 1.0, "e", null, null);
		AiFindingCreationRequest noVulnerability = new AiFindingCreationRequest(10L, null, null, 1.0,
				FindingStatus.SUGGESTED, 1.0, "e", null, null);
		AiFindingCreationRequest noSeverity = new AiFindingCreationRequest(10L, null, 5L, null,
				FindingStatus.SUGGESTED, 1.0, "e", null, null);
		AiFindingCreationRequest noConfidence = new AiFindingCreationRequest(10L, null, 5L, 1.0,
				FindingStatus.SUGGESTED, null, "e", null, null);
		AiFindingCreationRequest noStatus = new AiFindingCreationRequest(10L, null, 5L, 1.0, null, 1.0, "e", null,
				null);
		AiFindingCreationRequest blankEvidence = new AiFindingCreationRequest(10L, null, 5L, 1.0,
				FindingStatus.SUGGESTED, 1.0, " ", null, null);

		for (AiFindingCreationRequest invalid : List.of(noRequest, noVulnerability, noSeverity, noConfidence,
				noStatus, blankEvidence))
			assertThrows(IllegalArgumentException.class, () -> service.createFinding(UUID_OF_CONTEXT, invalid));

		verify(findingService, never()).createFinding(any(), any());
	}

	// ---- getFindings

	@Test
	@SuppressWarnings("unchecked")
	void getFindingsRestrictsTheQueryToTheRequestsAndSetsOfTheContext() {
		contextFound();
		when(contextService.getRequestItemIds(context)).thenReturn(List.of(10L));
		when(contextService.getEqualitySetItemIds(context)).thenReturn(List.of(30L));
		Pageable pageable = PageRequest.of(0, 20);
		Page<Finding> page = new PageImpl<>(List.of());
		when(findingService.getFindings(any(Specification.class), eq(pageable))).thenReturn(page);

		assertSame(page, service.getFindings(UUID_OF_CONTEXT, null, null, null, null, null, pageable));

		verify(contextService).getRequestItemIds(context);
		verify(contextService).getEqualitySetItemIds(context);
		verify(findingService).getFindings(any(Specification.class), eq(pageable));
	}

	@Test
	void getFindingsOfAnUnknownContextIsNotFound() {
		when(contextService.getContextByUuid(UUID_OF_CONTEXT)).thenThrow(new EntityNotFoundException("AgentContext not found"));

		assertThrows(EntityNotFoundException.class,
				() -> service.getFindings(UUID_OF_CONTEXT, null, null, null, null, null, PageRequest.of(0, 20)));
	}

	// ---- getFinding

	@Test
	void getFindingOfAReadableRequestIsReturned() {
		Finding finding = finding(FindingCreator.USER, null, null);
		when(findingService.getFinding(3L)).thenReturn(finding);
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(true);

		assertSame(finding, service.getFinding(UUID_OF_CONTEXT, 3L));
	}

	@Test
	void getFindingOfARequestOutsideTheContextIsNotFound() {
		when(findingService.getFinding(3L)).thenReturn(finding(FindingCreator.USER, null, null));
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(false);

		EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
				() -> service.getFinding(UUID_OF_CONTEXT, 3L));
		assertEquals("Finding not found", ex.getMessage());
	}

	@Test
	void getFindingThatDoesNotExistIsNotFound() {
		when(findingService.getFinding(3L)).thenThrow(new EntityNotFoundException("Finding not found"));

		assertThrows(EntityNotFoundException.class, () -> service.getFinding(UUID_OF_CONTEXT, 3L));
	}

	// ---- patchFinding

	private final FindingPatchRequest patch = new FindingPatchRequest(Optional.of(6.0), Optional.empty(),
			Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), false);

	private Finding visible(Finding finding) {
		when(findingService.getFinding(3L)).thenReturn(finding);
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(true);
		return finding;
	}

	@Test
	void patchFindingOfTheAgentOfTheContextIsApplied() {
		Finding finding = visible(finding(FindingCreator.AI, context, null));

		service.patchFinding(UUID_OF_CONTEXT, 3L, patch);

		verify(findingService).patchFinding(finding, patch);
	}

	@Test
	void patchFindingOfAnotherContextIsAConflict() {
		AgentContext other = AgentContext.builder().id(2L).uuid(UUID.randomUUID()).build();
		visible(finding(FindingCreator.AI, other, null));

		assertThrows(InvalidFindingStateException.class, () -> service.patchFinding(UUID_OF_CONTEXT, 3L, patch));
		verify(findingService, never()).patchFinding(any(Finding.class), any());
	}

	@Test
	void patchFindingOfAUserIsAConflict() {
		visible(finding(FindingCreator.USER, null, null));

		assertThrows(InvalidFindingStateException.class, () -> service.patchFinding(UUID_OF_CONTEXT, 3L, patch));
		verify(findingService, never()).patchFinding(any(Finding.class), any());
	}

	@Test
	void patchFindingWithoutAContextIsAConflict() {
		visible(finding(FindingCreator.AI, null, null));

		assertThrows(InvalidFindingStateException.class, () -> service.patchFinding(UUID_OF_CONTEXT, 3L, patch));
	}

	@Test
	void patchFindingReviewedByAHumanIsAConflict() {
		visible(finding(FindingCreator.AI, context, HumanVerdict.FALSE_POSITIVE));

		assertThrows(InvalidFindingStateException.class, () -> service.patchFinding(UUID_OF_CONTEXT, 3L, patch));
		verify(findingService, never()).patchFinding(any(Finding.class), any());
	}

	@Test
	void patchFindingOutsideTheContextIsNotFound() {
		when(findingService.getFinding(3L)).thenReturn(finding(FindingCreator.AI, context, null));
		contextFound();
		when(contextService.isRequestInContext(context, 10L)).thenReturn(false);

		assertThrows(EntityNotFoundException.class, () -> service.patchFinding(UUID_OF_CONTEXT, 3L, patch));
		verify(findingService, never()).patchFinding(any(Finding.class), any());
	}

}
