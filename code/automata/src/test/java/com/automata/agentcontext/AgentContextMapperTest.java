package com.automata.agentcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.automata.agentcontext.common.dto.AgentContextDetailedResponse;
import com.automata.agentcontext.common.dto.AgentContextItemResponse;
import com.automata.agentcontext.common.dto.AgentContextSummaryResponse;
import com.automata.agentcontext.common.enums.AgentContextItemType;
import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.host.Host;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.request.Request;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;

class AgentContextMapperTest {

	private Host host(Long id) {
		Host h = new Host();
		h.setId(id);
		return h;
	}

	@Test
	void toDetailedResponseMapsAllFields() {
		UUID uuid = UUID.randomUUID();
		Instant now = Instant.now();

		Routine routine = new Routine();
		routine.setId(5L);

		Request req = Request.of(10L);
		AgentContextItem item = AgentContextItem.builder().id(1L).itemType(AgentContextItemType.REQUEST)
				.request(req).build();

		AgentContext ctx = AgentContext.builder().id(1L).uuid(uuid).name("ctx-name").description("desc")
				.host(host(7L)).defaultAutoApproval(true).representativeStrategy(RepresentativeStrategy.LATEST)
				.allowedRoutines(List.of(routine)).defaultDuration(Duration.SHORT)
				.defaultVerbosity(ResultsVerbosity.VERBOSE).defaultPriority(3).maxRate(100)
				.creationDate(now).items(List.of(item)).build();

		AgentContextDetailedResponse response = AgentContextMapper.toDetailedResponse(ctx);

		assertEquals(1L, response.id());
		assertEquals(uuid, response.uuid());
		assertEquals("ctx-name", response.name());
		assertEquals("desc", response.description());
		assertEquals(7L, response.hostId());
		assertEquals(true, response.defaultAutoApproval());
		assertEquals(RepresentativeStrategy.LATEST, response.representativeStrategy());
		assertEquals(List.of(5L), response.allowedRoutineIds());
		assertEquals(Duration.SHORT, response.defaultDuration());
		assertEquals(ResultsVerbosity.VERBOSE, response.defaultVerbosity());
		assertEquals(3, response.defaultPriority());
		assertEquals(100, response.maxRate());
		assertEquals(now, response.creationDate());
		assertEquals(1, response.items().size());
		assertEquals(AgentContextItemType.REQUEST, response.items().get(0).itemType());
	}

	@Test
	void toSummaryResponseMapsFields() {
		UUID uuid = UUID.randomUUID();
		Instant now = Instant.now();

		AgentContextItem item1 = AgentContextItem.builder().id(1L).itemType(AgentContextItemType.REQUEST)
				.build();
		AgentContextItem item2 = AgentContextItem.builder().id(2L)
				.itemType(AgentContextItemType.EQUALITY_SET).build();

		AgentContext ctx = AgentContext.builder().id(2L).uuid(uuid).name("summary-ctx").host(host(3L))
				.representativeStrategy(RepresentativeStrategy.AI_JUDGEMENT).creationDate(now)
				.items(List.of(item1, item2)).build();

		AgentContextSummaryResponse response = AgentContextMapper.toSummaryResponse(ctx);

		assertEquals(2L, response.id());
		assertEquals(uuid, response.uuid());
		assertEquals("summary-ctx", response.name());
		assertEquals(3L, response.hostId());
		assertEquals(RepresentativeStrategy.AI_JUDGEMENT, response.representativeStrategy());
		assertEquals(2, response.itemCount());
		assertEquals(now, response.creationDate());
	}

	@Test
	void toItemResponseMapsRequestItem() {
		Request req = Request.of(10L);
		AgentContextItem item = AgentContextItem.builder().id(5L).itemType(AgentContextItemType.REQUEST)
				.request(req).build();

		AgentContextItemResponse response = AgentContextMapper.toItemResponse(item);

		assertEquals(5L, response.id());
		assertEquals(AgentContextItemType.REQUEST, response.itemType());
		assertEquals(10L, response.requestId());
		assertNull(response.equalitySetId());
	}

	@Test
	void toItemResponseMapsEqualitySetItem() {
		RequestEqualitySet set = RequestEqualitySet.builder().id(20L).alias("a").build();
		AgentContextItem item = AgentContextItem.builder().id(6L)
				.itemType(AgentContextItemType.EQUALITY_SET).equalitySet(set).build();

		AgentContextItemResponse response = AgentContextMapper.toItemResponse(item);

		assertEquals(6L, response.id());
		assertEquals(AgentContextItemType.EQUALITY_SET, response.itemType());
		assertNull(response.requestId());
		assertEquals(20L, response.equalitySetId());
	}

	@Test
	void toDetailedResponseListsTheTenantIds() {
		com.automata.tenant.Tenant t1 = com.automata.tenant.Tenant.builder().id(4L).build();
		com.automata.tenant.Tenant t2 = com.automata.tenant.Tenant.builder().id(9L).build();
		AgentContext ctx = AgentContext.builder().id(1L).host(host(7L)).tenants(List.of(t1, t2)).build();

		assertEquals(List.of(4L, 9L), AgentContextMapper.toDetailedResponse(ctx).tenantIds());
	}

	@Test
	void toDetailedResponseWithoutTenantsHasAnEmptyList() {
		AgentContext ctx = AgentContext.builder().id(1L).host(host(7L)).build();

		assertEquals(List.of(), AgentContextMapper.toDetailedResponse(ctx).tenantIds());
	}

	@Test
	void toTenantResponseCarriesTheAuthenticationAndTheRequests() {
		com.automata.tenant.authentication.StaticAuthData authData = new com.automata.tenant.authentication.StaticAuthData();
		com.automata.tenant.Tenant tenant = com.automata.tenant.Tenant.builder().id(4L).name("alice")
				.email("a@a.com").authentication(
						com.automata.tenant.authentication.Authentication.builder().authData(authData).build())
				.build();

		com.automata.agentcontext.common.dto.AgentContextTenantResponse response = AgentContextMapper
				.toTenantResponse(tenant, List.of(10L, 12L));

		assertEquals(4L, response.tenantId());
		assertEquals("alice", response.name());
		assertEquals("a@a.com", response.email());
		assertEquals(authData, response.authData());
		assertEquals(List.of(10L, 12L), response.requestIds());
	}

	@Test
	void toTenantResponseWithoutAuthenticationHasNullAuthData() {
		com.automata.tenant.Tenant tenant = com.automata.tenant.Tenant.builder().id(4L).name("alice")
				.email("a@a.com").build();

		assertNull(AgentContextMapper.toTenantResponse(tenant, List.of()).authData());
	}

	private com.automata.finding.Finding finding(com.automata.finding.common.enums.FindingCreator createdBy,
			AgentContext findingContext) {
		com.automata.vulnerability.Vulnerability vulnerability = new com.automata.vulnerability.Vulnerability();
		vulnerability.setId(4L);
		return com.automata.finding.Finding.builder().id(1L).request(Request.of(10L)).host(host(2L))
				.vulnerability(vulnerability).severity(7.5).confidence(8.0)
				.status(com.automata.finding.common.enums.FindingStatus.SUGGESTED).evidence("evidence")
				.createdBy(createdBy).agentContext(findingContext).humanComment("looks real")
				.humanVerdict(com.automata.finding.common.enums.HumanVerdict.TRUE_POSITIVE).build();
	}

	@Test
	void toAgentFindingDetailMapsTheFieldsAndTheOwnOrigin() {
		UUID uuid = UUID.randomUUID();
		AgentContext ctx = AgentContext.builder().id(3L).uuid(uuid).build();

		var detail = AgentContextMapper.toAgentFindingDetail(
				finding(com.automata.finding.common.enums.FindingCreator.AI, ctx), uuid);

		assertEquals(10L, detail.requestId());
		assertEquals(2L, detail.hostId());
		assertEquals(4L, detail.vulnerabilityId());
		assertEquals("evidence", detail.evidence());
		assertEquals("looks real", detail.humanComment());
		assertNull(detail.jobId());
		assertEquals(com.automata.finding.common.enums.FindingOrigin.THIS_CONTEXT, detail.origin());
	}

	@Test
	void toAgentFindingSummaryTellsTheOriginOfFindingsOfOtherContextsAndUsers() {
		UUID uuid = UUID.randomUUID();
		AgentContext other = AgentContext.builder().id(5L).uuid(UUID.randomUUID()).build();

		assertEquals(com.automata.finding.common.enums.FindingOrigin.OTHER_CONTEXT,
				AgentContextMapper.toAgentFindingSummary(
						finding(com.automata.finding.common.enums.FindingCreator.AI, other), uuid).origin());
		assertEquals(com.automata.finding.common.enums.FindingOrigin.USER,
				AgentContextMapper.toAgentFindingSummary(
						finding(com.automata.finding.common.enums.FindingCreator.USER, null), uuid).origin());
	}

}
