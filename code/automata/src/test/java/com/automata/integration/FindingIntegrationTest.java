package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.automata.finding.Finding;
import com.automata.finding.FindingRepository;
import com.automata.finding.FindingService;
import com.automata.finding.FindingUtils;
import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingFilter;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.dto.FindingReviewRequest;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.host.Host;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.common.enums.Method;
import com.automata.request.equalityset.Equalization;
import com.automata.request.equalityset.RequestEqualityRelation;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.comparator.Comparator;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;
import com.automata.testsupport.KnownBug;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.EntityNotFoundException;

@RepositoryIntegrationTest
@Import(FindingService.class)
class FindingIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	FindingService service;

	@Autowired
	FindingRepository findingRepo;

	Fixtures fx;

	Program program;

	Host host;

	Request request;

	Vulnerability vulnerability;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
		host = fx.host(program, "api.example.com");
		request = fx.request(host, Method.GET, "/a");
		vulnerability = fx.vulnerability("IDOR", null);
	}

	private FindingCreationRequest creation(Request onRequest, Long jobId, double severity, FindingStatus status, String poc) {
		return new FindingCreationRequest(onRequest.getId(), jobId, vulnerability.getId(), severity, status, 6, "evidence", poc,
				FindingCreator.USER, null);
	}

	@Test
	void createdFindingTakesTheHostOfItsRequestAndKeepsEveryField() {
		Finding created = service.createFinding(creation(request, null, 7.5, FindingStatus.SUGGESTED, null));
		fx.clear();

		Finding loaded = findingRepo.findById(created.getId()).orElseThrow();
		assertEquals(host.getId(), loaded.getHost().getId());
		assertEquals(request.getId(), loaded.getRequest().getId());
		assertEquals(vulnerability.getId(), loaded.getVulnerability().getId());
		assertEquals(7.5, loaded.getSeverity());
		assertEquals(6.0, loaded.getConfidence());
		assertEquals("evidence", loaded.getEvidence());
		assertNull(loaded.getJob());
		assertNull(loaded.getHumanVerdict());
		assertNotNull(loaded.getCreationDate());
	}

	@Test
	void longEvidenceIsStored() {
		String longEvidence = "x".repeat(20_000);

		Finding created = service.createFinding(new FindingCreationRequest(request.getId(), null, vulnerability.getId(), 1, FindingStatus.SUGGESTED, 1,
				longEvidence, null, FindingCreator.USER, null));
		fx.clear();

		assertEquals(20_000, findingRepo.findById(created.getId()).orElseThrow().getEvidence().length());
	}

	@Test
	void aConfirmedFindingNeedsItsSteps() {
		assertThrows(IllegalArgumentException.class, () -> service.createFinding(creation(request, null, 5, FindingStatus.CONFIRMED, " ")));
		Finding created = service.createFinding(creation(request, null, 5, FindingStatus.CONFIRMED, "1. do this"));
		assertEquals("1. do this", created.getPocReproductionSteps());
	}

	@Test
	void unknownVulnerabilityAndUnknownJobAreNotFound() {
		FindingCreationRequest unknownVuln = new FindingCreationRequest(request.getId(), null, -1L, 1, FindingStatus.SUGGESTED, 1, "e", null,
				FindingCreator.USER, null);
		FindingCreationRequest unknownJob = creation(request, -1L, 1, FindingStatus.SUGGESTED, null);

		assertThrows(EntityNotFoundException.class, () -> service.createFinding(unknownVuln));
		assertThrows(EntityNotFoundException.class, () -> service.createFinding(unknownJob));
	}

	@Test
	void aFindingCanReferenceAJob() {
		Routine routine = fx.routine("fuzz");
		NarrowHttpJob job = fx.narrowJob(routine, host, JobState.FINISHED, 1, Duration.SHORT, 1);

		Finding created = service.createFinding(creation(request, job.getId(), 5, FindingStatus.SUGGESTED, null));
		fx.clear();

		assertEquals(job.getId(), findingRepo.findById(created.getId()).orElseThrow().getJob().getId());
	}

	@Test
	void theFilterNarrowsByEveryColumn() {
		Request other = fx.request(fx.host(program, "www.example.com"), Method.GET, "/b");
		Finding a = service.createFinding(creation(request, null, 5, FindingStatus.SUGGESTED, null));
		Finding b = service.createFinding(creation(other, null, 6, FindingStatus.CONFIRMED, "steps"));
		service.reviewFinding(b.getId(), new FindingReviewRequest(HumanVerdict.TRUE_POSITIVE, "ok"));
		fx.clear();

		assertEquals(List.of(a.getId(), b.getId()), ids(new FindingFilter(null, null, null, null, null, null)));
		assertEquals(List.of(a.getId()), ids(new FindingFilter(host.getId(), null, null, null, null, null)));
		assertEquals(List.of(b.getId()), ids(new FindingFilter(null, other.getId(), null, null, null, null)));
		assertEquals(List.of(b.getId()), ids(new FindingFilter(null, null, null, FindingStatus.CONFIRMED, null, null)));
		assertEquals(List.of(b.getId()), ids(new FindingFilter(null, null, null, null, HumanVerdict.TRUE_POSITIVE, null)));
		assertEquals(List.of(a.getId(), b.getId()), ids(new FindingFilter(null, null, null, null, null, FindingCreator.USER)));
		assertEquals(List.of(), ids(new FindingFilter(null, null, null, null, null, FindingCreator.AI)));
		assertEquals(List.of(), ids(new FindingFilter(null, null, -1L, null, null, null)));
	}

	@Test
	void findingsOfRequestsAndOfEqualitySetsAreFoundTogether() {
		Request listed = request;
		Request member = fx.request(host, Method.GET, "/m");
		Request outsider = fx.request(host, Method.GET, "/o");
		Comparator comparator = fx.persist(Comparator.builder().name("c").build());
		Equalization equalization = fx.persist(Equalization.builder().host(host).comparator(comparator).build());
		RequestEqualitySet set = fx.persist(RequestEqualitySet.builder().alias("s").numberOfRequests(1).equalization(equalization).build());
		fx.persist(RequestEqualityRelation.builder().set(set).request(member).build());
		Finding f1 = service.createFinding(creation(listed, null, 1, FindingStatus.SUGGESTED, null));
		Finding f2 = service.createFinding(creation(member, null, 1, FindingStatus.SUGGESTED, null));
		service.createFinding(creation(outsider, null, 1, FindingStatus.SUGGESTED, null));
		fx.clear();

		var found = service.getFindings(FindingUtils.inRequestsOrEqualitySets(List.of(listed.getId()), List.of(set.getId())),
				PageRequest.of(0, 10, Sort.by("id")));
		var none = service.getFindings(FindingUtils.inRequestsOrEqualitySets(List.of(), List.of()), PageRequest.of(0, 10));

		assertEquals(List.of(f1.getId(), f2.getId()), found.map(Finding::getId).getContent());
		assertTrue(none.isEmpty());
	}

	@Test
	void patchPersistsAndKeepsTheRulesOfAConfirmedFinding() {
		Finding created = service.createFinding(creation(request, null, 5, FindingStatus.SUGGESTED, null));
		fx.clear();

		service.patchFinding(created.getId(), new FindingPatchRequest(Optional.of(9.0), Optional.of(FindingStatus.CONFIRMED), Optional.empty(),
				Optional.of("new evidence"), Optional.of("steps"), false, Optional.empty(), false));
		fx.clear();

		Finding loaded = findingRepo.findById(created.getId()).orElseThrow();
		assertEquals(9.0, loaded.getSeverity());
		assertEquals(FindingStatus.CONFIRMED, loaded.getStatus());
		assertEquals("steps", loaded.getPocReproductionSteps());
		assertThrows(IllegalArgumentException.class, () -> service.patchFinding(created.getId(), new FindingPatchRequest(Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true, Optional.empty(), false)));
	}

	@Test
	void reviewStoresTheVerdictTheCommentAndWhen() {
		Finding created = service.createFinding(creation(request, null, 5, FindingStatus.SUGGESTED, null));
		fx.clear();

		service.reviewFinding(created.getId(), new FindingReviewRequest(HumanVerdict.FALSE_POSITIVE, "not exploitable"));
		fx.clear();

		Finding loaded = findingRepo.findById(created.getId()).orElseThrow();
		assertEquals(HumanVerdict.FALSE_POSITIVE, loaded.getHumanVerdict());
		assertEquals("not exploitable", loaded.getHumanComment());
		assertNotNull(loaded.getReviewedAt());
	}

	@Test
	@KnownBug("BUG-037")
	void reviewWithoutAVerdictIsRefused() {
		Finding created = service.createFinding(creation(request, null, 5, FindingStatus.SUGGESTED, null));

		assertThrows(IllegalArgumentException.class, () -> service.reviewFinding(created.getId(), new FindingReviewRequest(null, "hm")));
	}

	@Test
	@KnownBug("BUG-016")
	void aFindingWithoutEvidenceIsRefusedAsABadRequest() {
		FindingCreationRequest noEvidence = new FindingCreationRequest(request.getId(), null, vulnerability.getId(), 1, FindingStatus.SUGGESTED, 1,
				null, null, FindingCreator.USER, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(noEvidence));
	}

	@Test
	@KnownBug("BUG-016")
	void aFindingWithoutStatusOrCreatorIsRefusedAsABadRequest() {
		FindingCreationRequest noStatus = new FindingCreationRequest(request.getId(), null, vulnerability.getId(), 1, null, 1, "e", null,
				FindingCreator.USER, null);
		FindingCreationRequest noCreator = new FindingCreationRequest(request.getId(), null, vulnerability.getId(), 1, FindingStatus.SUGGESTED, 1,
				"e", null, null, null);

		assertThrows(IllegalArgumentException.class, () -> service.createFinding(noStatus));
		assertThrows(IllegalArgumentException.class, () -> service.createFinding(noCreator));
	}

	@Test
	void deleteRemovesTheFinding() {
		Finding created = service.createFinding(creation(request, null, 5, FindingStatus.SUGGESTED, null));
		fx.clear();

		service.deleteFinding(created.getId());
		fx.clear();

		assertTrue(findingRepo.findById(created.getId()).isEmpty());
	}

	private List<Long> ids(FindingFilter filter) {
		return service.getFindings(filter, PageRequest.of(0, 20, Sort.by("id"))).map(Finding::getId).getContent();
	}
}
