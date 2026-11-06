package com.automata.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.agentcontext.AgentContext;
import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.dto.FindingReviewRequest;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.host.Host;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.repository.HttpJobRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.common.enums.RequestContentType;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class FindingServiceTest {

	@Mock
	FindingRepository findingRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	HttpJobRepository jobRepo;
	@Mock
	VulnerabilityRepository vulnerabilityRepo;

	@InjectMocks
	FindingService service;

	private Host host(Long id) {
		Host h = new Host();
		h.setId(id);
		return h;
	}

	private Request request(Long id) {
		Request r = Request.of(id);
		r.setHost(host(1L));
		r.setContentType(RequestContentType.JSON);
		return r;
	}

	private Vulnerability vulnerability(Long id) {
		Vulnerability v = new Vulnerability();
		v.setId(id);
		v.setName("XSS");
		return v;
	}

	// ---- createFinding

	@Test
	void createFindingSuggestedWithoutPoc() {
		Request req = request(10L);
		Vulnerability vuln = vulnerability(5L);
		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(vulnerabilityRepo.findById(5L)).thenReturn(Optional.of(vuln));
		when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 7.5,
				FindingStatus.SUGGESTED, 8.0, "Found reflected XSS", null, FindingCreator.AI, "AI desc");

		Finding finding = service.createFinding(creation);

		assertEquals(req, finding.getRequest());
		assertEquals(req.getHost(), finding.getHost());
		assertNull(finding.getJob());
		assertEquals(vuln, finding.getVulnerability());
		assertEquals(7.5, finding.getSeverity());
		assertEquals(FindingStatus.SUGGESTED, finding.getStatus());
		assertEquals(8.0, finding.getConfidence());
		assertEquals("Found reflected XSS", finding.getEvidence());
		assertEquals(FindingCreator.AI, finding.getCreatedBy());
	}

	@Test
	void createFindingConfirmedWithPoc() {
		Request req = request(10L);
		Vulnerability vuln = vulnerability(5L);
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(3L);

		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(jobRepo.findById(3L)).thenReturn(Optional.of(job));
		when(vulnerabilityRepo.findById(5L)).thenReturn(Optional.of(vuln));
		when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		FindingCreationRequest creation = new FindingCreationRequest(10L, 3L, 5L, 9.0,
				FindingStatus.CONFIRMED, 9.5, "SQL injection found", "Step 1: ...", FindingCreator.USER,
				null);

		Finding finding = service.createFinding(creation);

		assertEquals(job, finding.getJob());
		assertEquals(FindingStatus.CONFIRMED, finding.getStatus());
		assertEquals("Step 1: ...", finding.getPocReproductionSteps());
	}

	@Test
	void createFindingConfirmedWithoutPocThrows() {
		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 7.0,
				FindingStatus.CONFIRMED, 8.0, "evidence", null, FindingCreator.AI, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingConfirmedWithBlankPocThrows() {
		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 7.0,
				FindingStatus.CONFIRMED, 8.0, "evidence", "   ", FindingCreator.AI, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingSeverityTooHighThrows() {
		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 10.1,
				FindingStatus.SUGGESTED, 5.0, "evidence", null, FindingCreator.AI, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingSeverityNegativeThrows() {
		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, -0.1,
				FindingStatus.SUGGESTED, 5.0, "evidence", null, FindingCreator.AI, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingConfidenceTooHighThrows() {
		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 5.0,
				FindingStatus.SUGGESTED, 10.1, "evidence", null, FindingCreator.AI, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingConfidenceNegativeThrows() {
		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 5.0,
				FindingStatus.SUGGESTED, -0.1, "evidence", null, FindingCreator.AI, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingRequestNotFoundThrows() {
		when(requestRepo.findById(99L)).thenReturn(Optional.empty());

		FindingCreationRequest creation = new FindingCreationRequest(99L, null, 5L, 5.0,
				FindingStatus.SUGGESTED, 5.0, "evidence", null, FindingCreator.AI, null);

		assertThrows(EntityNotFoundException.class, () -> service.createFinding(creation));
	}

	@Test
	void createFindingHostDerivedFromRequest() {
		Host h = host(7L);
		Request req = Request.of(10L);
		req.setHost(h);
		req.setContentType(RequestContentType.JSON);
		Vulnerability vuln = vulnerability(5L);

		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(vulnerabilityRepo.findById(5L)).thenReturn(Optional.of(vuln));
		when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		FindingCreationRequest creation = new FindingCreationRequest(10L, null, 5L, 5.0,
				FindingStatus.SUGGESTED, 5.0, "evidence", null, FindingCreator.AI, null);

		Finding finding = service.createFinding(creation);

		assertEquals(h, finding.getHost());
	}

	@Test
	void createFindingWithAContextLinksTheFindingToIt() {
		Request req = request(10L);
		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(vulnerabilityRepo.findById(5L)).thenReturn(Optional.of(vulnerability(5L)));
		when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		AgentContext context = AgentContext.builder().id(2L).build();

		Finding finding = service.createFinding(new FindingCreationRequest(10L, null, 5L, 7.5,
				FindingStatus.SUGGESTED, 8.0, "evidence", null, FindingCreator.AI, null), context);

		assertEquals(context, finding.getAgentContext());
	}

	@Test
	void createFindingWithoutAContextLeavesItNull() {
		when(requestRepo.findById(10L)).thenReturn(Optional.of(request(10L)));
		when(vulnerabilityRepo.findById(5L)).thenReturn(Optional.of(vulnerability(5L)));
		when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Finding finding = service.createFinding(new FindingCreationRequest(10L, null, 5L, 7.5,
				FindingStatus.SUGGESTED, 8.0, "evidence", null, FindingCreator.USER, null));

		assertNull(finding.getAgentContext());
	}

	// ---- getFindings by specification

	@Test
	void getFindingsBySpecificationQueriesTheRepository() {
		org.springframework.data.jpa.domain.Specification<Finding> spec = (root, query, cb) -> null;
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 5);
		org.springframework.data.domain.Page<Finding> page = new org.springframework.data.domain.PageImpl<>(
				java.util.List.of());
		when(findingRepo.findAll(spec, pageable)).thenReturn(page);

		assertEquals(page, service.getFindings(spec, pageable));
	}

	// ---- patchFinding

	@Test
	void patchFindingOfAnEntityAppliesThePatchWithoutLoadingIt() {
		Finding finding = Finding.builder().id(1L).severity(3.0).status(FindingStatus.SUGGESTED).confidence(5.0)
				.evidence("old").build();
		FindingPatchRequest patch = new FindingPatchRequest(Optional.of(6.0), Optional.empty(), Optional.empty(),
				Optional.of("new"), Optional.empty(), false, Optional.empty(), false);

		service.patchFinding(finding, patch);

		assertEquals(6.0, finding.getSeverity());
		assertEquals("new", finding.getEvidence());
		verify(findingRepo).save(finding);
		verify(findingRepo, org.mockito.Mockito.never()).findById(any());
	}

	@Test
	void patchFindingUpdatesSeverity() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));
		when(findingRepo.save(any())).thenReturn(finding);

		FindingPatchRequest patch = new FindingPatchRequest(Optional.of(8.0), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), false);

		service.patchFinding(1L, patch);

		assertEquals(8.0, finding.getSeverity());
	}

	@Test
	void patchFindingInvalidSeverityThrows() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));

		FindingPatchRequest patch = new FindingPatchRequest(Optional.of(11.0), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), false);

		assertThrows(IllegalArgumentException.class, () -> service.patchFinding(1L, patch));
	}

	@Test
	void patchFindingSetsAiDescriptionNull() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").aiDescription("old")
				.createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));
		when(findingRepo.save(any())).thenReturn(finding);

		FindingPatchRequest patch = new FindingPatchRequest(Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), true);

		service.patchFinding(1L, patch);

		assertNull(finding.getAiDescription());
	}

	@Test
	void patchFindingStatusToConfirmedWithoutPocThrows() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));

		FindingPatchRequest patch = new FindingPatchRequest(Optional.empty(), Optional.of(FindingStatus.CONFIRMED),
				Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), false);

		assertThrows(IllegalArgumentException.class, () -> service.patchFinding(1L, patch));
	}

	@Test
	void patchFindingStatusToConfirmedWithPocSucceeds() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").pocReproductionSteps("steps")
				.createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));
		when(findingRepo.save(any())).thenReturn(finding);

		FindingPatchRequest patch = new FindingPatchRequest(Optional.empty(), Optional.of(FindingStatus.CONFIRMED),
				Optional.empty(), Optional.empty(), Optional.empty(), false, Optional.empty(), false);

		service.patchFinding(1L, patch);

		assertEquals(FindingStatus.CONFIRMED, finding.getStatus());
	}

	@Test
	void patchFindingNullingPocOnConfirmedThrows() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.CONFIRMED).evidence("e").pocReproductionSteps("steps")
				.createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));

		FindingPatchRequest patch = new FindingPatchRequest(Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), true, Optional.empty(), false);

		assertThrows(IllegalArgumentException.class, () -> service.patchFinding(1L, patch));
	}

	// ---- reviewFinding

	@Test
	void reviewFindingSetsVerdictAndTimestamp() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));
		when(findingRepo.save(any())).thenReturn(finding);

		FindingReviewRequest review = new FindingReviewRequest(HumanVerdict.TRUE_POSITIVE, "Looks legit");

		service.reviewFinding(1L, review);

		assertEquals(HumanVerdict.TRUE_POSITIVE, finding.getHumanVerdict());
		assertEquals("Looks legit", finding.getHumanComment());
		assertNotNull(finding.getReviewedAt());
	}

	@Test
	void reviewFindingFalsePositive() {
		Finding finding = Finding.builder().id(1L).severity(5.0).confidence(5.0)
				.status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));
		when(findingRepo.save(any())).thenReturn(finding);

		FindingReviewRequest review = new FindingReviewRequest(HumanVerdict.FALSE_POSITIVE,
				"Not exploitable in this context");

		service.reviewFinding(1L, review);

		assertEquals(HumanVerdict.FALSE_POSITIVE, finding.getHumanVerdict());
		assertEquals("Not exploitable in this context", finding.getHumanComment());
	}

	// ---- deleteFinding

	@Test
	void deleteFindingSucceeds() {
		Finding finding = Finding.builder().id(1L).build();
		when(findingRepo.findById(1L)).thenReturn(Optional.of(finding));

		service.deleteFinding(1L);

		verify(findingRepo).delete(finding);
	}

	@Test
	void deleteFindingNotFoundThrows() {
		when(findingRepo.findById(99L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.deleteFinding(99L));
	}

	// ---- getFinding

	@Test
	void getFindingNotFoundThrows() {
		when(findingRepo.findById(99L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getFinding(99L));
	}

	// ---- boundary values

	@Test
	void createFindingSeverityAtBoundaries() {
		Request req = request(10L);
		Vulnerability vuln = vulnerability(5L);
		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(vulnerabilityRepo.findById(5L)).thenReturn(Optional.of(vuln));
		when(findingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		FindingCreationRequest zeroSev = new FindingCreationRequest(10L, null, 5L, 0.0,
				FindingStatus.SUGGESTED, 0.0, "evidence", null, FindingCreator.AI, null);
		Finding f1 = service.createFinding(zeroSev);
		assertEquals(0.0, f1.getSeverity());
		assertEquals(0.0, f1.getConfidence());

		FindingCreationRequest maxSev = new FindingCreationRequest(10L, null, 5L, 10.0,
				FindingStatus.SUGGESTED, 10.0, "evidence", null, FindingCreator.AI, null);
		Finding f2 = service.createFinding(maxSev);
		assertEquals(10.0, f2.getSeverity());
		assertEquals(10.0, f2.getConfidence());
	}

}
