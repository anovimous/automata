package com.automata.agentcontext;

import java.util.List;
import java.util.UUID;

import com.automata.agentcontext.common.dto.AgentContextDetailedResponse;
import com.automata.agentcontext.common.dto.AgentFindingDetailedResponse;
import com.automata.agentcontext.common.dto.AgentFindingSummaryResponse;
import com.automata.agentcontext.common.dto.AgentContextItemResponse;
import com.automata.agentcontext.common.dto.AgentContextSummaryResponse;
import com.automata.agentcontext.common.dto.AgentContextTenantResponse;
import com.automata.finding.Finding;
import com.automata.finding.common.enums.FindingOrigin;
import com.automata.routine.Routine;
import com.automata.tenant.Tenant;

public abstract class AgentContextMapper {

	public static AgentContextDetailedResponse toDetailedResponse(AgentContext context) {
		return AgentContextDetailedResponse.builder().id(context.getId()).uuid(context.getUuid())
				.name(context.getName()).description(context.getDescription()).hostId(context.getHost().getId())
				.defaultAutoApproval(context.isDefaultAutoApproval())
				.representativeStrategy(context.getRepresentativeStrategy())
				.allowedRoutineIds(context.getAllowedRoutines().stream().map(Routine::getId).toList())
				.tenantIds(context.getTenants().stream().map(Tenant::getId).toList())
				.defaultDuration(context.getDefaultDuration()).defaultVerbosity(context.getDefaultVerbosity())
				.defaultPriority(context.getDefaultPriority()).maxRate(context.getMaxRate())
				.creationDate(context.getCreationDate()).items(context.getItems().stream()
						.map(AgentContextMapper::toItemResponse).toList())
				.build();
	}

	public static AgentContextSummaryResponse toSummaryResponse(AgentContext context) {
		return AgentContextSummaryResponse.builder().id(context.getId()).uuid(context.getUuid())
				.name(context.getName()).hostId(context.getHost().getId())
				.representativeStrategy(context.getRepresentativeStrategy())
				.itemCount(context.getItems().size()).creationDate(context.getCreationDate()).build();
	}

	public static AgentContextItemResponse toItemResponse(AgentContextItem item) {
		return AgentContextItemResponse.builder().id(item.getId()).itemType(item.getItemType())
				.requestId(item.getRequest() != null ? item.getRequest().getId() : null)
				.equalitySetId(item.getEqualitySet() != null ? item.getEqualitySet().getId() : null).build();
	}

	public static AgentContextTenantResponse toTenantResponse(Tenant tenant, List<Long> requestIds) {
		return AgentContextTenantResponse.builder().tenantId(tenant.getId()).name(tenant.getName())
				.email(tenant.getEmail())
				.authData(tenant.getAuthentication() != null ? tenant.getAuthentication().getAuthData() : null)
				.requestIds(requestIds).build();
	}

	public static AgentFindingSummaryResponse toAgentFindingSummary(Finding finding, UUID contextUuid) {
		return AgentFindingSummaryResponse.builder().id(finding.getId()).requestId(finding.getRequest().getId())
				.hostId(finding.getHost().getId()).vulnerabilityId(finding.getVulnerability().getId())
				.severity(finding.getSeverity()).status(finding.getStatus()).confidence(finding.getConfidence())
				.createdBy(finding.getCreatedBy()).origin(originOf(finding, contextUuid))
				.humanVerdict(finding.getHumanVerdict()).creationDate(finding.getCreationDate()).build();
	}

	public static AgentFindingDetailedResponse toAgentFindingDetail(Finding finding, UUID contextUuid) {
		return AgentFindingDetailedResponse.builder().id(finding.getId()).creationDate(finding.getCreationDate())
				.requestId(finding.getRequest().getId()).hostId(finding.getHost().getId())
				.jobId(finding.getJob() != null ? finding.getJob().getId() : null)
				.vulnerabilityId(finding.getVulnerability().getId()).severity(finding.getSeverity())
				.status(finding.getStatus()).confidence(finding.getConfidence()).evidence(finding.getEvidence())
				.pocReproductionSteps(finding.getPocReproductionSteps()).createdBy(finding.getCreatedBy())
				.origin(originOf(finding, contextUuid)).aiDescription(finding.getAiDescription())
				.humanVerdict(finding.getHumanVerdict()).humanComment(finding.getHumanComment())
				.reviewedAt(finding.getReviewedAt()).build();
	}

	private static FindingOrigin originOf(Finding finding, UUID contextUuid) {
		UUID findingContextUuid = finding.getAgentContext() == null ? null : finding.getAgentContext().getUuid();
		return FindingOrigin.of(finding.getCreatedBy(), findingContextUuid, contextUuid);
	}

	public static List<AgentContextItemResponse> toItemResponses(List<AgentContextItem> items) {
		return items.stream().map(AgentContextMapper::toItemResponse).toList();
	}

}
