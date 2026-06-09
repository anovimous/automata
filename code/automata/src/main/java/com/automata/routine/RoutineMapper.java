package com.automata.routine;

import org.springframework.data.domain.Page;

import com.automata.routine.common.dto.RoutineDetailedResponseDto;
import com.automata.routine.common.dto.RoutineSummaryResponseDto;

public abstract class RoutineMapper {

	public static RoutineDetailedResponseDto toDetailedResponse(Routine routine) {
		return RoutineDetailedResponseDto.builder().id(routine.getId()).creationDate(routine.getCreationDate())
				.key(routine.getKey()).description(routine.getDescription()).updatedAt(routine.getUpdatedAt())
				.isAvailableAtConsumer(routine.isAvailableAtConsumer()).overhead(routine.getOverhead())
				.protocol(routine.getProtocol()).allowedTargets(routine.getAllowedTargets())
				.vulnerabilityId(routine.getVulnerability() != null ? routine.getVulnerability().getId() : null)
				.build();
	}

	public static Page<RoutineSummaryResponseDto> toSummaryResponse(Page<Routine> routines) {

		return routines.map(RoutineMapper::toSummaryResponse);
	}

	public static RoutineSummaryResponseDto toSummaryResponse(Routine routine) {

		return RoutineSummaryResponseDto.builder().id(routine.getId()).creationDate(routine.getCreationDate())
				.key(routine.getKey()).isAvailableAtConsumer(routine.isAvailableAtConsumer())
				.overhead(routine.getOverhead()).protocol(routine.getProtocol())
				.allowedTargets(routine.getAllowedTargets()).build();
	}

}
