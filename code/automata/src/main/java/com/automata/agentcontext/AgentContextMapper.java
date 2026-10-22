package com.automata.agentcontext;

import java.util.List;

import com.automata.agentcontext.common.dto.AgentContextDetailedResponse;
import com.automata.agentcontext.common.dto.AgentContextItemResponse;
import com.automata.agentcontext.common.dto.AgentContextSummaryResponse;
import com.automata.routine.Routine;

public abstract class AgentContextMapper {

	public static AgentContextDetailedResponse toDetailedResponse(AgentContext context) {
		return AgentContextDetailedResponse.builder().id(context.getId()).uuid(context.getUuid())
				.name(context.getName()).description(context.getDescription()).hostId(context.getHost().getId())
				.defaultAutoApproval(context.isDefaultAutoApproval())
				.representativeStrategy(context.getRepresentativeStrategy())
				.allowedRoutineIds(context.getAllowedRoutines().stream().map(Routine::getId).toList())
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

	public static List<AgentContextItemResponse> toItemResponses(List<AgentContextItem> items) {
		return items.stream().map(AgentContextMapper::toItemResponse).toList();
	}

}
