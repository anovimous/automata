package com.automata.agentcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.agentcontext.common.dto.AgentContextCreationRequest;
import com.automata.agentcontext.common.dto.AgentContextItemAddRequest;
import com.automata.agentcontext.common.dto.AgentContextPatchRequest;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.agentcontext.common.enums.AgentContextItemType;
import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.RequestTenantPair;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.equalityset.Equalization;
import com.automata.request.equalityset.RequestEqualityRelation;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualityRelationSummaryInternalDto;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class AgentContextServiceTest {

	@Mock
	AgentContextRepository contextRepo;
	@Mock
	AgentContextItemRepository itemRepo;
	@Mock
	HostRepository hostRepo;
	@Mock
	RoutineRepository routineRepo;
	@Mock
	RequestRepository requestRepo;
	@Mock
	RequestEqualitySetRepository equalitySetRepo;
	@Mock
	RequestEqualityRelationRepository equalityRelationRepo;
	@Mock
	TenantRepository tenantRepo;

	@InjectMocks
	AgentContextService service;

	private Host host(Long id) {
		Host h = new Host();
		h.setId(id);
		return h;
	}

	// ---- createContext

	@Test
	void createContextSavesWithHost() {
		Host h = host(1L);
		when(hostRepo.findById(1L)).thenReturn(Optional.of(h));
		when(contextRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		AgentContextCreationRequest request = new AgentContextCreationRequest("ctx", "desc", 1L, false,
				RepresentativeStrategy.LATEST, null, null, null, null, null, null);

		AgentContext ctx = service.createContext(request);

		assertEquals("ctx", ctx.getName());
		assertEquals(h, ctx.getHost());
		assertFalse(ctx.isDefaultAutoApproval());
	}

	@Test
	void createContextWithUnknownHostThrows() {
		when(hostRepo.findById(99L)).thenReturn(Optional.empty());

		AgentContextCreationRequest request = new AgentContextCreationRequest("ctx", null, 99L, false,
				RepresentativeStrategy.LATEST, null, null, null, null, null, null);

		assertThrows(EntityNotFoundException.class, () -> service.createContext(request));
	}

	// ---- patchContext

	@Test
	void patchContextUpdatesName() {
		AgentContext ctx = AgentContext.builder().id(1L).name("old").host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(contextRepo.save(any())).thenReturn(ctx);

		AgentContextPatchRequest patch = new AgentContextPatchRequest(Optional.of("new"), Optional.empty(),
				false, Optional.empty(), Optional.empty(), null, Optional.empty(), false, Optional.empty(),
				false, Optional.empty(), false, Optional.empty(), false);

		service.patchContext(1L, patch);

		assertEquals("new", ctx.getName());
	}

	@Test
	void patchContextSetsDescriptionNull() {
		AgentContext ctx = AgentContext.builder().id(1L).name("n").description("old desc").host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(contextRepo.save(any())).thenReturn(ctx);

		AgentContextPatchRequest patch = new AgentContextPatchRequest(Optional.empty(), Optional.empty(), true,
				Optional.empty(), Optional.empty(), null, Optional.empty(), false, Optional.empty(), false,
				Optional.empty(), false, Optional.empty(), false);

		service.patchContext(1L, patch);

		assertEquals(null, ctx.getDescription());
	}

	// ---- addItem (REQUEST)

	@Test
	void addRequestItemValidatesHost() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		Host otherHost = host(2L);
		Request req = Request.of(10L);
		req.setHost(otherHost);
		req.setContentType(RequestContentType.JSON);
		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.REQUEST, 10L,
				null);

		assertThrows(IllegalArgumentException.class, () -> service.addItem(1L, request));
	}

	@Test
	void addRequestItemSucceeds() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		Request req = Request.of(10L);
		req.setHost(h);
		req.setContentType(RequestContentType.JSON);
		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(itemRepo.existsByContextIdAndRequestId(1L, 10L)).thenReturn(false);
		when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.REQUEST, 10L,
				null);

		AgentContextItem item = service.addItem(1L, request);

		assertEquals(AgentContextItemType.REQUEST, item.getItemType());
		assertEquals(req, item.getRequest());
	}

	@Test
	void addDuplicateRequestItemThrows() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		Request req = Request.of(10L);
		req.setHost(h);
		when(requestRepo.findById(10L)).thenReturn(Optional.of(req));
		when(itemRepo.existsByContextIdAndRequestId(1L, 10L)).thenReturn(true);

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.REQUEST, 10L,
				null);

		assertThrows(IllegalArgumentException.class, () -> service.addItem(1L, request));
	}

	// ---- addItem (EQUALITY_SET)

	@Test
	void addEqualitySetItemValidatesHost() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		Equalization eq = Equalization.builder().id(5L).host(host(2L)).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(20L).equalization(eq).alias("a").build();
		when(equalitySetRepo.findById(20L)).thenReturn(Optional.of(set));

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET,
				null, 20L);

		assertThrows(IllegalArgumentException.class, () -> service.addItem(1L, request));
	}

	@Test
	void addEqualitySetItemSucceeds() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		Equalization eq = Equalization.builder().id(5L).host(h).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(20L).equalization(eq).alias("a").build();
		when(equalitySetRepo.findById(20L)).thenReturn(Optional.of(set));
		when(itemRepo.existsByContextIdAndEqualitySetId(1L, 20L)).thenReturn(false);
		when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET,
				null, 20L);

		AgentContextItem item = service.addItem(1L, request);

		assertEquals(AgentContextItemType.EQUALITY_SET, item.getItemType());
		assertEquals(set, item.getEqualitySet());
	}

	// ---- removeItem

	@Test
	void removeItemOfOtherContextThrows() {
		AgentContext ctx1 = AgentContext.builder().id(1L).host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		AgentContextItem item = AgentContextItem.builder().id(10L).context(ctx1).build();
		when(itemRepo.findById(10L)).thenReturn(Optional.of(item));

		assertThrows(IllegalArgumentException.class, () -> service.removeItem(2L, 10L));
	}

	@Test
	void removeItemSucceeds() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		AgentContextItem item = AgentContextItem.builder().id(10L).context(ctx).build();
		when(itemRepo.findById(10L)).thenReturn(Optional.of(item));

		service.removeItem(1L, 10L);

		verify(itemRepo).delete(item);
	}

	// ---- deleteContext

	@Test
	void deleteContextRemovesItemsFirst() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		AgentContextItem item = AgentContextItem.builder().id(10L).context(ctx).build();
		ctx.setItems(List.of(item));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		service.deleteContext(1L);

		verify(itemRepo).deleteAll(List.of(item));
		verify(contextRepo).delete(ctx);
	}

	// ---- getContext

	@Test
	void getContextThrowsWhenNotFound() {
		when(contextRepo.findById(99L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getContext(99L));
	}

	@Test
	void addRequestItemRequiresRequestId() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.REQUEST, null,
				null);

		assertThrows(IllegalArgumentException.class, () -> service.addItem(1L, request));
	}

	@Test
	void addEqualitySetItemRequiresSetId() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		AgentContextItemAddRequest request = new AgentContextItemAddRequest(AgentContextItemType.EQUALITY_SET,
				null, null);

		assertThrows(IllegalArgumentException.class, () -> service.addItem(1L, request));
	}

	// ---- resolveContextTargets

	private Request eligibleRequest(Long id) {
		Request r = Request.of(id);
		r.setContentType(RequestContentType.JSON);
		r.setBodySizeBytes(10);
		return r;
	}

	private Request ineligibleRequest(Long id) {
		Request r = Request.of(id);
		r.setContentType(RequestContentType.MULTIPART);
		r.setBodySizeBytes(10);
		return r;
	}

	@Test
	void resolveTargetsForRequestItems() {
		Host h = host(1L);
		Request req = eligibleRequest(10L);

		AgentContextItem item = AgentContextItem.builder().id(1L).itemType(AgentContextItemType.REQUEST)
				.request(req).build();
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).defaultAutoApproval(true)
				.items(List.of(item)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		List<ResolvedTarget> targets = service.resolveContextTargets(1L);

		assertEquals(1, targets.size());
		assertEquals(10L, targets.get(0).requestId());
		assertTrue(targets.get(0).automataJobEligible());
		assertTrue(targets.get(0).autoApproval());
		assertEquals(AgentContextItemType.REQUEST, targets.get(0).sourceItemType());
	}

	@Test
	void resolveTargetsIneligibleRequest() {
		Host h = host(1L);
		Request req = ineligibleRequest(10L);

		AgentContextItem item = AgentContextItem.builder().id(1L).itemType(AgentContextItemType.REQUEST)
				.request(req).build();
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).defaultAutoApproval(false)
				.items(List.of(item)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		List<ResolvedTarget> targets = service.resolveContextTargets(1L);

		assertEquals(1, targets.size());
		assertFalse(targets.get(0).automataJobEligible());
		assertFalse(targets.get(0).autoApproval());
	}

	@Test
	void resolveTargetsLatestStrategyPicksLatest() {
		Host h = host(1L);
		Request latest = eligibleRequest(20L);
		Equalization eq = Equalization.builder().id(5L).host(h).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(30L).equalization(eq).alias("s")
				.autoApproval(true).build();

		AgentContextItem item = AgentContextItem.builder().id(1L)
				.itemType(AgentContextItemType.EQUALITY_SET).equalitySet(set).build();
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).defaultAutoApproval(false)
				.items(List.of(item)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(equalityRelationRepo.findTopBySetOrderByRequestInsertionDateDesc(set)).thenReturn(latest);

		List<ResolvedTarget> targets = service.resolveContextTargets(1L);

		assertEquals(1, targets.size());
		assertEquals(20L, targets.get(0).requestId());
		assertTrue(targets.get(0).autoApproval());
		assertEquals(AgentContextItemType.EQUALITY_SET, targets.get(0).sourceItemType());
		assertEquals(30L, targets.get(0).sourceEqualitySetId());
	}

	@Test
	void resolveTargetsAiJudgementReturnsAllRelations() {
		Host h = host(1L);
		Request r1 = eligibleRequest(10L);
		Request r2 = eligibleRequest(11L);
		Equalization eq = Equalization.builder().id(5L).host(h).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(30L).equalization(eq).alias("s")
				.autoApproval(false).build();

		RequestEqualityRelation rel1 = RequestEqualityRelation.builder().request(r1).set(set).build();
		RequestEqualityRelation rel2 = RequestEqualityRelation.builder().request(r2).set(set).build();

		AgentContextItem item = AgentContextItem.builder().id(1L)
				.itemType(AgentContextItemType.EQUALITY_SET).equalitySet(set).build();
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.AI_JUDGEMENT).defaultAutoApproval(false)
				.items(List.of(item)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(equalityRelationRepo.findAllBySet(set)).thenReturn(List.of(rel1, rel2));

		List<ResolvedTarget> targets = service.resolveContextTargets(1L);

		assertEquals(2, targets.size());
		assertEquals(10L, targets.get(0).requestId());
		assertEquals(11L, targets.get(1).requestId());
	}

	@Test
	void resolveTargetsDeduplicatesByRequestId() {
		Host h = host(1L);
		Request req = eligibleRequest(10L);

		Equalization eq = Equalization.builder().id(5L).host(h).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(30L).equalization(eq).alias("s")
				.autoApproval(true).build();

		AgentContextItem directItem = AgentContextItem.builder().id(1L)
				.itemType(AgentContextItemType.REQUEST).request(req).build();
		AgentContextItem setItem = AgentContextItem.builder().id(2L)
				.itemType(AgentContextItemType.EQUALITY_SET).equalitySet(set).build();

		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).defaultAutoApproval(false)
				.items(List.of(directItem, setItem)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(equalityRelationRepo.findTopBySetOrderByRequestInsertionDateDesc(set)).thenReturn(req);

		List<ResolvedTarget> targets = service.resolveContextTargets(1L);

		assertEquals(1, targets.size());
		assertEquals(10L, targets.get(0).requestId());
		// The later entry (equality set) overwrites the first (direct), so set auto-approval wins
		assertTrue(targets.get(0).autoApproval());
		assertEquals(AgentContextItemType.EQUALITY_SET, targets.get(0).sourceItemType());
	}

	@Test
	void resolveTargetsEmptySetReturnsNothing() {
		Host h = host(1L);
		Equalization eq = Equalization.builder().id(5L).host(h).build();
		RequestEqualitySet set = RequestEqualitySet.builder().id(30L).equalization(eq).alias("s")
				.autoApproval(false).build();

		AgentContextItem item = AgentContextItem.builder().id(1L)
				.itemType(AgentContextItemType.EQUALITY_SET).equalitySet(set).build();
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).defaultAutoApproval(false)
				.items(List.of(item)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(equalityRelationRepo.findTopBySetOrderByRequestInsertionDateDesc(set)).thenReturn(null);

		List<ResolvedTarget> targets = service.resolveContextTargets(1L);

		assertTrue(targets.isEmpty());
	}

	// ---- tenants

	private Tenant tenant(Long id, Host host) {
		return Tenant.builder().id(id).name("t" + id).email("t" + id + "@a.com").host(host).build();
	}

	private AgentContextCreationRequest creationWithTenants(java.util.List<Long> tenantIds) {
		return new AgentContextCreationRequest("ctx", null, 1L, false, RepresentativeStrategy.LATEST, null, null,
				null, null, null, tenantIds);
	}

	@Test
	void createContextWithTenantsStoresThem() {
		Host h = host(1L);
		when(hostRepo.findById(1L)).thenReturn(Optional.of(h));
		when(contextRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tenantRepo.findAllById(List.of(4L, 5L))).thenReturn(List.of(tenant(4L, h), tenant(5L, h)));

		AgentContext ctx = service.createContext(creationWithTenants(List.of(4L, 5L)));

		assertEquals(List.of(4L, 5L), ctx.getTenants().stream().map(Tenant::getId).toList());
	}

	@Test
	void createContextWithoutTenantsHasNone() {
		when(hostRepo.findById(1L)).thenReturn(Optional.of(host(1L)));
		when(contextRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		assertTrue(service.createContext(creationWithTenants(null)).getTenants().isEmpty());
		assertTrue(service.createContext(creationWithTenants(List.of())).getTenants().isEmpty());
	}

	@Test
	void createContextIgnoresDuplicateTenantIds() {
		Host h = host(1L);
		when(hostRepo.findById(1L)).thenReturn(Optional.of(h));
		when(contextRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tenantRepo.findAllById(List.of(4L))).thenReturn(List.of(tenant(4L, h)));

		AgentContext ctx = service.createContext(creationWithTenants(List.of(4L, 4L)));

		assertEquals(1, ctx.getTenants().size());
	}

	@Test
	void createContextWithUnknownTenantThrows() {
		Host h = host(1L);
		when(hostRepo.findById(1L)).thenReturn(Optional.of(h));
		when(tenantRepo.findAllById(List.of(4L, 5L))).thenReturn(List.of(tenant(4L, h)));

		assertThrows(EntityNotFoundException.class, () -> service.createContext(creationWithTenants(List.of(4L, 5L))));
	}

	@Test
	void createContextWithTenantOfOtherHostThrows() {
		Host h = host(1L);
		when(hostRepo.findById(1L)).thenReturn(Optional.of(h));
		when(tenantRepo.findAllById(List.of(4L))).thenReturn(List.of(tenant(4L, host(2L))));

		assertThrows(IllegalArgumentException.class, () -> service.createContext(creationWithTenants(List.of(4L))));
	}

	@Test
	void addTenantAddsItToTheContext() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h).build();
		Tenant t = tenant(4L, h);
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(tenantRepo.findById(4L)).thenReturn(Optional.of(t));

		service.addTenant(1L, 4L);

		assertEquals(List.of(t), ctx.getTenants());
		verify(contextRepo).save(ctx);
	}

	@Test
	void addTenantOfOtherHostThrows() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(1L)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(tenantRepo.findById(4L)).thenReturn(Optional.of(tenant(4L, host(2L))));

		assertThrows(IllegalArgumentException.class, () -> service.addTenant(1L, 4L));
		assertTrue(ctx.getTenants().isEmpty());
	}

	@Test
	void addTenantTwiceThrows() {
		Host h = host(1L);
		Tenant t = tenant(4L, h);
		AgentContext ctx = AgentContext.builder().id(1L).host(h).tenants(new java.util.ArrayList<>(List.of(t))).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(tenantRepo.findById(4L)).thenReturn(Optional.of(t));

		assertThrows(IllegalArgumentException.class, () -> service.addTenant(1L, 4L));
		assertEquals(1, ctx.getTenants().size());
	}

	@Test
	void addUnknownTenantThrows() {
		when(contextRepo.findById(1L)).thenReturn(Optional.of(AgentContext.builder().id(1L).host(host(1L)).build()));
		when(tenantRepo.findById(4L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.addTenant(1L, 4L));
	}

	@Test
	void addTenantToUnknownContextThrows() {
		when(contextRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.addTenant(1L, 4L));
	}

	@Test
	void removeTenantTakesItOutOfTheContext() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).host(h)
				.tenants(new java.util.ArrayList<>(List.of(tenant(4L, h), tenant(5L, h)))).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		service.removeTenant(1L, 4L);

		assertEquals(List.of(5L), ctx.getTenants().stream().map(Tenant::getId).toList());
		verify(contextRepo).save(ctx);
	}

	@Test
	void removeTenantThatIsNotInTheContextThrows() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(1L)).build();
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		assertThrows(EntityNotFoundException.class, () -> service.removeTenant(1L, 4L));
	}

	@Test
	void removeTenantOfUnknownContextThrows() {
		when(contextRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.removeTenant(1L, 4L));
	}

	// ---- isRequestInContext / requireRequestInContext

	private RequestEqualitySet setOf(Long id, Host h) {
		return RequestEqualitySet.builder().id(id).equalization(Equalization.builder().id(5L).host(h).build())
				.alias("s").build();
	}

	private AgentContextItem setItem(RequestEqualitySet set) {
		return AgentContextItem.builder().itemType(AgentContextItemType.EQUALITY_SET).equalitySet(set).build();
	}

	private AgentContextItem requestItem(Long requestId) {
		return AgentContextItem.builder().itemType(AgentContextItemType.REQUEST).request(Request.of(requestId))
				.build();
	}

	private AgentContext contextOf(RepresentativeStrategy strategy, AgentContextItem... items) {
		return AgentContext.builder().id(1L).host(host(1L)).representativeStrategy(strategy)
				.items(List.of(items)).build();
	}

	@Test
	void requestItemPutsItsRequestInTheContext() {
		AgentContext ctx = contextOf(RepresentativeStrategy.LATEST, requestItem(10L));

		assertTrue(service.isRequestInContext(ctx, 10L));
		assertFalse(service.isRequestInContext(ctx, 11L));
	}

	@Test
	void emptyContextHasNoRequests() {
		assertFalse(service.isRequestInContext(contextOf(RepresentativeStrategy.LATEST), 10L));
		assertFalse(service.isRequestInContext(contextOf(RepresentativeStrategy.AI_JUDGEMENT), 10L));
	}

	@Test
	void latestStrategyAlsoPutsEveryRequestOfASetInTheContext() {
		RequestEqualitySet set = setOf(30L, host(1L));
		when(equalityRelationRepo.existsBySetAndRequestId(set, 20L)).thenReturn(true);
		when(equalityRelationRepo.existsBySetAndRequestId(set, 21L)).thenReturn(true);
		when(equalityRelationRepo.existsBySetAndRequestId(set, 22L)).thenReturn(false);
		AgentContext ctx = contextOf(RepresentativeStrategy.LATEST, setItem(set));

		assertTrue(service.isRequestInContext(ctx, 20L));
		assertTrue(service.isRequestInContext(ctx, 21L));
		assertFalse(service.isRequestInContext(ctx, 22L));
		verify(equalityRelationRepo, never()).findTopBySetOrderByRequestInsertionDateDesc(any());
	}

	@Test
	void latestStrategyWithAnEmptySetHasNoRequest() {
		RequestEqualitySet set = setOf(30L, host(1L));
		when(equalityRelationRepo.existsBySetAndRequestId(set, 20L)).thenReturn(false);

		assertFalse(service.isRequestInContext(contextOf(RepresentativeStrategy.LATEST, setItem(set)), 20L));
	}

	@Test
	void aiJudgementStrategyPutsEveryRequestOfASetInTheContext() {
		RequestEqualitySet set = setOf(30L, host(1L));
		when(equalityRelationRepo.existsBySetAndRequestId(set, 20L)).thenReturn(true);
		when(equalityRelationRepo.existsBySetAndRequestId(set, 21L)).thenReturn(false);
		AgentContext ctx = contextOf(RepresentativeStrategy.AI_JUDGEMENT, setItem(set));

		assertTrue(service.isRequestInContext(ctx, 20L));
		assertFalse(service.isRequestInContext(ctx, 21L));
		verify(equalityRelationRepo, never()).findTopBySetOrderByRequestInsertionDateDesc(any());
	}

	@Test
	void anyOfSeveralItemsCanPutARequestInTheContext() {
		RequestEqualitySet first = setOf(30L, host(1L));
		RequestEqualitySet second = setOf(31L, host(1L));
		when(equalityRelationRepo.existsBySetAndRequestId(first, 20L)).thenReturn(false);
		when(equalityRelationRepo.existsBySetAndRequestId(second, 20L)).thenReturn(true);
		AgentContext ctx = contextOf(RepresentativeStrategy.AI_JUDGEMENT, requestItem(10L), setItem(first),
				setItem(second));

		assertTrue(service.isRequestInContext(ctx, 20L));
		assertTrue(service.isRequestInContext(ctx, 10L));
	}

	@Test
	void requireRequestInContextAcceptsARequestOfTheContext() {
		java.util.UUID uuid = java.util.UUID.randomUUID();
		when(contextRepo.findByUuid(uuid))
				.thenReturn(Optional.of(contextOf(RepresentativeStrategy.LATEST, requestItem(10L))));

		service.requireRequestInContext(uuid, 10L);
	}

	@Test
	void requireRequestInContextAnswersNotFoundForARequestOutsideTheContext() {
		java.util.UUID uuid = java.util.UUID.randomUUID();
		when(contextRepo.findByUuid(uuid))
				.thenReturn(Optional.of(contextOf(RepresentativeStrategy.LATEST, requestItem(10L))));

		assertThrows(EntityNotFoundException.class, () -> service.requireRequestInContext(uuid, 11L));
	}

	@Test
	void requireRequestInContextOfAnUnknownContextIsNotFound() {
		java.util.UUID uuid = java.util.UUID.randomUUID();
		when(contextRepo.findByUuid(uuid)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.requireRequestInContext(uuid, 10L));
	}

	// ---- item id helpers

	@Test
	void requestItemIdsListOnlyTheRequestItems() {
		AgentContext ctx = contextOf(RepresentativeStrategy.LATEST, requestItem(10L), setItem(setOf(30L, host(1L))),
				requestItem(11L));

		assertEquals(List.of(10L, 11L), service.getRequestItemIds(ctx));
	}

	@Test
	void equalitySetItemIdsListOnlyTheSetItems() {
		AgentContext ctx = contextOf(RepresentativeStrategy.LATEST, requestItem(10L), setItem(setOf(30L, host(1L))),
				setItem(setOf(31L, host(1L))));

		assertEquals(List.of(30L, 31L), service.getEqualitySetItemIds(ctx));
	}

	@Test
	void itemIdsOfAnEmptyContextAreEmpty() {
		AgentContext ctx = contextOf(RepresentativeStrategy.LATEST);

		assertEquals(List.of(), service.getRequestItemIds(ctx));
		assertEquals(List.of(), service.getEqualitySetItemIds(ctx));
	}

	// ---- getContextRequests

	private com.automata.request.Request requestOf(Long id, String path) {
		com.automata.request.Request req = com.automata.request.Request.of(id);
		req.setMethod(com.automata.request.common.enums.Method.GET);
		req.setComputatedPath(path);
		req.setInsertionDate(java.time.LocalDate.of(2026, 10, 20));
		return req;
	}

	private AgentContext contextWithUuid(RepresentativeStrategy strategy, AgentContextItem... items) {
		AgentContext ctx = AgentContext.builder().id(1L).uuid(java.util.UUID.randomUUID()).host(host(1L))
				.representativeStrategy(strategy).items(List.of(items)).build();
		when(contextRepo.findByUuid(ctx.getUuid())).thenReturn(Optional.of(ctx));
		return ctx;
	}

	@Test
	void contextRequestsListTheRequestItemsAndEverySetMemberWithTheResolvedFlag() {
		RequestEqualitySet set = setOf(30L, host(1L));
		AgentContext ctx = contextWithUuid(RepresentativeStrategy.LATEST, requestItem(10L), setItem(set));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(equalityRelationRepo.findTopBySetOrderByRequestInsertionDateDesc(set)).thenReturn(Request.of(21L));
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(requestRepo.findInContext(List.of(10L), List.of(30L), pageable)).thenReturn(
				new org.springframework.data.domain.PageImpl<>(
						List.of(requestOf(21L, "/a"), requestOf(20L, "/a"), requestOf(10L, "/b")), pageable, 3));
		when(equalityRelationRepo.findSetIdsOfRequests(List.of(21L, 20L, 10L), List.of(30L)))
				.thenReturn(List.of(new RequestEqualityRelationSummaryInternalDto(30L, 21L),
						new RequestEqualityRelationSummaryInternalDto(30L, 20L)));

		var page = service.getContextRequests(ctx.getUuid(), null, pageable);

		assertEquals(3, page.getTotalElements());
		assertEquals(21L, page.getContent().get(0).requestId());
		assertEquals(30L, page.getContent().get(0).equalitySetId());
		assertTrue(page.getContent().get(0).resolvedTarget());
		assertEquals(20L, page.getContent().get(1).requestId());
		assertFalse(page.getContent().get(1).resolvedTarget());
		assertEquals(30L, page.getContent().get(1).equalitySetId());
		assertEquals(10L, page.getContent().get(2).requestId());
		assertTrue(page.getContent().get(2).resolvedTarget());
		assertEquals(null, page.getContent().get(2).equalitySetId());
		assertEquals("/b", page.getContent().get(2).computatedPath());
	}

	@Test
	void contextRequestsFilteredByASetListOnlyItsMembers() {
		RequestEqualitySet set = setOf(30L, host(1L));
		AgentContext ctx = contextWithUuid(RepresentativeStrategy.AI_JUDGEMENT, requestItem(10L), setItem(set));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(equalityRelationRepo.findRequestsInSet(30L, pageable)).thenReturn(
				new org.springframework.data.domain.PageImpl<>(List.of(requestOf(20L, "/a")), pageable, 1));
		when(equalityRelationRepo.findSetIdsOfRequests(List.of(20L), List.of(30L)))
				.thenReturn(List.of(new RequestEqualityRelationSummaryInternalDto(30L, 20L)));
		when(equalityRelationRepo.findAllBySet(set)).thenReturn(List.of());

		var page = service.getContextRequests(ctx.getUuid(), 30L, pageable);

		assertEquals(1, page.getContent().size());
		assertEquals(30L, page.getContent().get(0).equalitySetId());
		verify(requestRepo, never()).findInContext(any(), any(), any());
	}

	@Test
	void contextRequestsFilteredByASetOutsideTheContextAreNotFound() {
		AgentContext ctx = contextWithUuid(RepresentativeStrategy.LATEST, setItem(setOf(30L, host(1L))));

		assertThrows(EntityNotFoundException.class, () -> service.getContextRequests(ctx.getUuid(), 31L,
				org.springframework.data.domain.PageRequest.of(0, 20)));
		verify(equalityRelationRepo, never()).findRequestsInSet(any(), any());
	}

	@Test
	void contextRequestsOfAnEmptyContextAreEmptyWithoutQueries() {
		AgentContext ctx = contextWithUuid(RepresentativeStrategy.LATEST);
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		var page = service.getContextRequests(ctx.getUuid(), null,
				org.springframework.data.domain.PageRequest.of(0, 20));

		assertTrue(page.isEmpty());
		verify(requestRepo, never()).findInContext(any(), any(), any());
	}

	@Test
	void contextRequestsWithOnlyRequestItemsSendAPlaceholderForTheMissingSets() {
		AgentContext ctx = contextWithUuid(RepresentativeStrategy.LATEST, requestItem(10L));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(requestRepo.findInContext(List.of(10L), List.of(-1L), pageable)).thenReturn(
				new org.springframework.data.domain.PageImpl<>(List.of(requestOf(10L, "/b")), pageable, 1));

		var page = service.getContextRequests(ctx.getUuid(), null, pageable);

		assertEquals(10L, page.getContent().get(0).requestId());
		verify(equalityRelationRepo, never()).findSetIdsOfRequests(any(), any());
	}

	@Test
	void contextRequestsIgnoreTheSortOfTheCaller() {
		AgentContext ctx = contextWithUuid(RepresentativeStrategy.LATEST, requestItem(10L));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		org.springframework.data.domain.Pageable unsorted = org.springframework.data.domain.PageRequest.of(1, 5);
		when(requestRepo.findInContext(List.of(10L), List.of(-1L), unsorted))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), unsorted, 0));

		service.getContextRequests(ctx.getUuid(), null, org.springframework.data.domain.PageRequest.of(1, 5,
				org.springframework.data.domain.Sort.by("method")));

		verify(requestRepo).findInContext(List.of(10L), List.of(-1L), unsorted);
	}

	@Test
	void contextRequestsOfAnUnknownContextAreNotFound() {
		java.util.UUID uuid = java.util.UUID.randomUUID();
		when(contextRepo.findByUuid(uuid)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getContextRequests(uuid, null,
				org.springframework.data.domain.PageRequest.of(0, 20)));
	}

	// ---- getContextTenants

	@Test
	void contextTenantsListEachTenantWithItsRequestsOfTheContext() {
		Host h = host(1L);
		Tenant alice = tenant(4L, h);
		Tenant bob = tenant(5L, h);
		AgentContext ctx = AgentContext.builder().id(1L).uuid(java.util.UUID.randomUUID()).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).tenants(List.of(alice, bob))
				.items(List.of(requestItem(10L), requestItem(11L), requestItem(12L))).build();
		when(contextRepo.findByUuid(ctx.getUuid())).thenReturn(Optional.of(ctx));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(requestRepo.findTenantPairsByIdIn(List.of(10L, 11L, 12L))).thenReturn(List.of(
				new RequestTenantPair(10L, 4L), new RequestTenantPair(11L, 5L), new RequestTenantPair(12L, 4L)));

		var tenants = service.getContextTenants(ctx.getUuid());

		assertEquals(2, tenants.size());
		assertEquals(4L, tenants.get(0).tenantId());
		assertEquals(List.of(10L, 12L), tenants.get(0).requestIds());
		assertEquals(5L, tenants.get(1).tenantId());
		assertEquals(List.of(11L), tenants.get(1).requestIds());
	}

	@Test
	void contextTenantWithoutRequestsInTheContextHasAnEmptyList() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).uuid(java.util.UUID.randomUUID()).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).tenants(List.of(tenant(4L, h)))
				.items(List.of(requestItem(10L))).build();
		when(contextRepo.findByUuid(ctx.getUuid())).thenReturn(Optional.of(ctx));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));
		when(requestRepo.findTenantPairsByIdIn(List.of(10L))).thenReturn(List.of());

		assertEquals(List.of(), service.getContextTenants(ctx.getUuid()).get(0).requestIds());
	}

	@Test
	void contextTenantsOfAContextWithoutRequestsDoNotQueryTheRequests() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).uuid(java.util.UUID.randomUUID()).host(h)
				.representativeStrategy(RepresentativeStrategy.LATEST).tenants(List.of(tenant(4L, h))).build();
		when(contextRepo.findByUuid(ctx.getUuid())).thenReturn(Optional.of(ctx));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		assertEquals(List.of(), service.getContextTenants(ctx.getUuid()).get(0).requestIds());

		verify(requestRepo, never()).findTenantPairsByIdIn(any());
	}

	@Test
	void contextWithoutTenantsHasNoTenants() {
		AgentContext ctx = AgentContext.builder().id(1L).uuid(java.util.UUID.randomUUID()).host(host(1L))
				.representativeStrategy(RepresentativeStrategy.LATEST).build();
		when(contextRepo.findByUuid(ctx.getUuid())).thenReturn(Optional.of(ctx));
		when(contextRepo.findById(1L)).thenReturn(Optional.of(ctx));

		assertTrue(service.getContextTenants(ctx.getUuid()).isEmpty());
	}

	@Test
	void contextTenantsOfAnUnknownContextIsNotFound() {
		java.util.UUID uuid = java.util.UUID.randomUUID();
		when(contextRepo.findByUuid(uuid)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getContextTenants(uuid));
	}

	// ---- getContextHost

	@Test
	void contextHostIsTheHostOfTheContext() {
		Host h = host(1L);
		AgentContext ctx = AgentContext.builder().id(1L).uuid(java.util.UUID.randomUUID()).host(h).build();
		when(contextRepo.findByUuid(ctx.getUuid())).thenReturn(Optional.of(ctx));

		assertEquals(h, service.getContextHost(ctx.getUuid()));
	}

	@Test
	void contextHostOfAnUnknownContextIsNotFound() {
		java.util.UUID uuid = java.util.UUID.randomUUID();
		when(contextRepo.findByUuid(uuid)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getContextHost(uuid));
	}

}
