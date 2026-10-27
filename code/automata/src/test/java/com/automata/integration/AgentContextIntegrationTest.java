package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.automata.agentcontext.AgentContext;
import com.automata.agentcontext.AgentContextItemRepository;
import com.automata.agentcontext.AgentContextRepository;
import com.automata.agentcontext.AgentContextService;
import com.automata.agentcontext.common.dto.AgentContextCreationRequest;
import com.automata.agentcontext.common.dto.AgentContextItemAddRequest;
import com.automata.agentcontext.common.dto.AgentContextPatchRequest;
import com.automata.agentcontext.common.dto.ContextRequestSummary;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.agentcontext.common.enums.AgentContextItemType;
import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.finding.Finding;
import com.automata.finding.FindingRepository;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.host.Host;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.common.enums.Method;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;
import com.automata.tenant.Tenant;
import com.automata.testsupport.KnownBug;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.EntityNotFoundException;

@RepositoryIntegrationTest
@Import(AgentContextService.class)
class AgentContextIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	AgentContextService service;

	@Autowired
	AgentContextRepository contextRepo;

	@Autowired
	AgentContextItemRepository itemRepo;

	@Autowired
	FindingRepository findingRepo;

	Fixtures fx;

	Program program;

	Host host;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
		host = fx.host(program, "api.example.com");
	}

	private AgentContext context(RepresentativeStrategy strategy, boolean autoApproval) {
		AgentContextCreationRequest request = new AgentContextCreationRequest("ctx", "d", host.getId(), autoApproval, strategy, null, null, null,
				null, 5, null);
		AgentContext created = service.createContext(request);
		fx.clear();
		return created;
	}

	private static Set<Long> requestIds(List<ResolvedTarget> targets) {
		return targets.stream().map(ResolvedTarget::requestId).collect(Collectors.toSet());
	}

	@Test
	void createdContextKeepsItsDefaultsAndTenantsAndAllowedRoutines() {
		Routine routine = fx.routine("fuzz");
		Tenant tenant = fx.tenant(host, "alice");
		AgentContextCreationRequest request = new AgentContextCreationRequest("ctx", "desc", host.getId(), true, RepresentativeStrategy.LATEST,
				List.of(routine.getId()), Duration.LONG, null, 3, 7, List.of(tenant.getId()));

		AgentContext created = service.createContext(request);
		fx.clear();

		AgentContext loaded = service.getContext(created.getId());
		assertEquals("ctx", loaded.getName());
		assertTrue(loaded.isDefaultAutoApproval());
		assertEquals(Duration.LONG, loaded.getDefaultDuration());
		assertEquals(3, loaded.getDefaultPriority());
		assertEquals(7, loaded.getMaxRate());
		assertEquals(1, loaded.getAllowedRoutines().size());
		assertEquals(1, loaded.getTenants().size());
		assertEquals(loaded, service.getContextByUuid(loaded.getUuid()));
	}

	@Test
	void contextsAreListedPerHost() {
		Host other = fx.host(program, "www.example.com");
		context(RepresentativeStrategy.LATEST, false);
		service.createContext(new AgentContextCreationRequest("other", null, other.getId(), false, RepresentativeStrategy.LATEST, null, null, null,
				null, 2, null));
		fx.clear();

		assertEquals(1, service.getContextsByHost(host.getId(), PageRequest.of(0, 10)).getTotalElements());
	}

	@Test
	void theLatestStrategyResolvesOnlyTheNewestMemberOfASet() {
		Request older = fx.request(host, Method.GET, "/a");
		Request newer = fx.request(host, Method.GET, "/a");
		RequestEqualitySet set = fx.equalitySet(host, true, older, newer);
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET, null, set.getId()));
		fx.clear();

		List<ResolvedTarget> targets = service.resolveContextTargets(context.getId());

		assertEquals(Set.of(newer.getId()), requestIds(targets));
		assertTrue(targets.get(0).autoApproval());
		assertEquals(set.getId(), targets.get(0).sourceEqualitySetId());
	}

	@Test
	void theAiJudgementStrategyResolvesEveryMemberOfASet() {
		Request a = fx.request(host, Method.GET, "/a");
		Request b = fx.request(host, Method.GET, "/a");
		RequestEqualitySet set = fx.equalitySet(host, false, a, b);
		AgentContext context = context(RepresentativeStrategy.AI_JUDGEMENT, false);
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET, null, set.getId()));
		fx.clear();

		assertEquals(Set.of(a.getId(), b.getId()), requestIds(service.resolveContextTargets(context.getId())));
	}

	@Test
	void aRequestItemTakesTheAutoApprovalOfTheContext() {
		Request request = fx.request(host, Method.GET, "/a");
		AgentContext context = context(RepresentativeStrategy.LATEST, true);
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, request.getId(), null));
		fx.clear();

		ResolvedTarget target = service.resolveContextTargets(context.getId()).get(0);

		assertTrue(target.autoApproval());
		assertEquals(AgentContextItemType.REQUEST, target.sourceItemType());
	}

	@Test
	void itemsOfAnotherHostAreRefusedAndDuplicatesToo() {
		Host other = fx.host(program, "www.example.com");
		Request foreign = fx.request(other, Method.GET, "/a");
		Request mine = fx.request(host, Method.GET, "/a");
		RequestEqualitySet foreignSet = fx.equalitySet(other, false, foreign);
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		fx.clear();

		assertThrows(IllegalArgumentException.class,
				() -> service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, foreign.getId(), null)));
		assertThrows(IllegalArgumentException.class,
				() -> service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET, null, foreignSet.getId())));
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, mine.getId(), null));
		em.flush();
		assertThrows(IllegalArgumentException.class,
				() -> service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, mine.getId(), null)));
	}

	@Test
	void membershipCoversRequestItemsAndEverySetMemberUnderBothStrategies() {
		Request direct = fx.request(host, Method.GET, "/d");
		Request older = fx.request(host, Method.GET, "/s");
		Request newer = fx.request(host, Method.GET, "/s");
		Request outsider = fx.request(host, Method.GET, "/o");
		RequestEqualitySet set = fx.equalitySet(host, false, older, newer);
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, direct.getId(), null));
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET, null, set.getId()));
		fx.clear();
		AgentContext loaded = service.getContext(context.getId());

		assertTrue(service.isRequestInContext(loaded, direct.getId()));
		assertTrue(service.isRequestInContext(loaded, older.getId()));
		assertTrue(service.isRequestInContext(loaded, newer.getId()));
		assertFalse(service.isRequestInContext(loaded, outsider.getId()));
		assertThrows(EntityNotFoundException.class, () -> service.requireRequestInContext(loaded.getUuid(), outsider.getId()));
	}

	@Test
	void contextRequestsListEverythingReadableNewestFirstWithTheResolvedFlag() {
		Request direct = fx.request(host, Method.GET, "/d");
		Request older = fx.request(host, Method.GET, "/s");
		Request newer = fx.request(host, Method.GET, "/s");
		RequestEqualitySet set = fx.equalitySet(host, false, older, newer);
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, direct.getId(), null));
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET, null, set.getId()));
		fx.clear();
		AgentContext loaded = service.getContext(context.getId());

		List<ContextRequestSummary> all = service.getContextRequests(loaded.getUuid(), null, PageRequest.of(0, 10)).getContent();

		assertEquals(List.of(newer.getId(), older.getId(), direct.getId()), all.stream().map(ContextRequestSummary::requestId).toList());
		assertEquals(List.of(true, false, true), all.stream().map(ContextRequestSummary::resolvedTarget).toList());
		assertEquals(set.getId(), all.get(0).equalitySetId());
		assertEquals(null, all.get(2).equalitySetId());
		List<ContextRequestSummary> onlySet = service.getContextRequests(loaded.getUuid(), set.getId(), PageRequest.of(0, 10)).getContent();
		assertEquals(2, onlySet.size());
		assertThrows(EntityNotFoundException.class, () -> service.getContextRequests(loaded.getUuid(), -1L, PageRequest.of(0, 10)));
	}

	@Test
	void contextTenantsListEachTenantWithTheRequestsOfTheContextItIsTiedTo() {
		Tenant alice = fx.tenant(host, "alice");
		Tenant bob = fx.tenant(host, "bob");
		Request request = fx.request(host, Method.GET, "/a");
		request.setTenant(alice);
		fx.persist(request);
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		service.addTenant(context.getId(), alice.getId());
		service.addTenant(context.getId(), bob.getId());
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, request.getId(), null));
		fx.clear();
		AgentContext loaded = service.getContext(context.getId());

		var tenants = service.getContextTenants(loaded.getUuid());

		assertEquals(2, tenants.size());
		var withRequests = tenants.stream().filter(t -> !t.requestIds().isEmpty()).toList();
		assertEquals(1, withRequests.size());
		assertEquals(List.of(request.getId()), withRequests.get(0).requestIds());
	}

	@Test
	void removingTheTenantTakesItOutOfTheContextOnly() {
		Tenant alice = fx.tenant(host, "alice");
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		service.addTenant(context.getId(), alice.getId());
		em.flush();
		fx.clear();

		service.removeTenant(context.getId(), alice.getId());
		fx.clear();

		assertTrue(service.getContext(context.getId()).getTenants().isEmpty());
		assertTrue(em.find(Tenant.class, alice.getId()) != null);
	}

	@Test
	void patchPersistsAndClearsTheOptionalDefaults() {
		AgentContext context = service.createContext(new AgentContextCreationRequest("ctx", "d", host.getId(), false, RepresentativeStrategy.LATEST,
				null, Duration.LONG, null, 3, 5, null));
		fx.clear();

		service.patchContext(context.getId(), new AgentContextPatchRequest(java.util.Optional.of("renamed"), java.util.Optional.empty(), true,
				java.util.Optional.empty(), java.util.Optional.empty(), null, java.util.Optional.empty(), true, java.util.Optional.empty(), false,
				java.util.Optional.empty(), true, java.util.Optional.of(9), false));
		fx.clear();

		AgentContext loaded = service.getContext(context.getId());
		assertEquals("renamed", loaded.getName());
		assertEquals(null, loaded.getDescription());
		assertEquals(null, loaded.getDefaultDuration());
		assertEquals(null, loaded.getDefaultPriority());
		assertEquals(9, loaded.getMaxRate());
	}

	@Test
	void deleteOfAContextWithItemsRemovesThem() {
		Request request = fx.request(host, Method.GET, "/a");
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		service.addItem(context.getId(), new AgentContextItemAddRequest(AgentContextItemType.REQUEST, request.getId(), null));
		fx.clear();

		service.deleteContext(context.getId());
		fx.clear();

		assertTrue(contextRepo.findById(context.getId()).isEmpty());
		assertEquals(0, itemRepo.count());
	}

	@Test
	@KnownBug("BUG-039")
	void deleteOfAContextWhoseAgentCreatedAJobOrAFindingIsRefusedOrKeepsTheirHistory() {
		Routine routine = fx.routine("fuzz");
		Request request = fx.request(host, Method.GET, "/a");
		Vulnerability vulnerability = fx.vulnerability("IDOR", null);
		AgentContext context = context(RepresentativeStrategy.LATEST, false);
		AgentContext managed = contextRepo.findById(context.getId()).orElseThrow();
		NarrowHttpJob job = fx.narrowJob(routine, host, JobState.FINISHED, 1, Duration.SHORT, 1);
		job.setAgentContext(managed);
		fx.persist(job);
		fx.persist(Finding.builder().request(request).host(host).vulnerability(vulnerability).severity(1).confidence(1)
				.status(FindingStatus.SUGGESTED).evidence("e").createdBy(FindingCreator.AI).agentContext(managed).build());
		fx.clear();

		// today the foreign keys of http_job.agent_context_id and finding.agent_context_id make the delete fail with a raw
		// database error (HTTP 500); the delete must either be refused with a clear conflict or detach the history
		try {
			service.deleteContext(context.getId());
			fx.clear();
		} catch (org.springframework.dao.DataIntegrityViolationException | jakarta.persistence.PersistenceException | IllegalStateException e) {
			throw new AssertionError("raw database error: " + e.getMessage(), e);
		}
		assertEquals(1, findingRepo.count());
	}
}
