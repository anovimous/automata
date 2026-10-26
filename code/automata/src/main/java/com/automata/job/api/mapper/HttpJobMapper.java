package com.automata.job.api.mapper;

import java.util.UUID;

import org.springframework.data.domain.Page;

import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.api.dto.HttpJobSummaryResponseDto;
import com.automata.job.api.dto.RequestJobResponseDto;
import com.automata.job.api.dto.RequestJobSummaryResponseDto;
import com.automata.job.domain.model.enums.JobOrigin;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.utils.HttpJobUtils;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
import com.automata.job.domain.valueobject.RequestJobSummaryInternalDto;
import com.automata.program.common.dto.ProgramSummaryDetailsResponseDto;
import com.automata.routine.common.dto.RoutineSummaryDetailsResponseDto;

public abstract class HttpJobMapper {

	public static HttpJobResponseDto toResponseDto(HttpJobFullDetailsInternalDto dto) {
		return HttpJobResponseDto.builder().jobId(dto.jobId()).httpJobScope(dto.httpJobScope())
				.currentState(dto.currentState()).requestedState(dto.requestedState()).verbosity(dto.verbosity())
				.priority(dto.priority()).rate(dto.rate()).targetSelector(dto.targetSelector())
				.genericConfig(dto.genericConfig()).customConfig(dto.customConfig())
				.program(ProgramSummaryDetailsResponseDto.builder().id(dto.programId()).name(dto.programName())
						.build())
				.routine(RoutineSummaryDetailsResponseDto.builder().id(dto.routineId()).key(dto.routineKey()).build())
				.failureMessage(dto.failureMessage()).configFileStatus(dto.configFileStatus())
				.configFileFailure(dto.configFileFailure()).aiDescription(dto.aiDescription())
				.aiResult(dto.aiResult()).createdBy(dto.createdBy()).approved(dto.approved())
				.terminal(dto.currentState().isTerminal()).waitingForAction(waitingForAction(dto))
				.agentContextUuid(dto.agentContextUuid()).build();
	}

	public static Page<HttpJobSummaryResponseDto> toResponseDtos(Page<HttpJobSummaryInternalDto> internalDtos) {
		return internalDtos.map(HttpJobMapper::toResponseDto);
	}

	public static HttpJobSummaryResponseDto toResponseDto(HttpJobSummaryInternalDto dto) {
		return HttpJobSummaryResponseDto.builder().id(dto.id()).creationDate(dto.creationDate()).scope(dto.scope())
				.currentState(dto.currentState()).priority(dto.priority()).rate(dto.rate()).routineId(dto.routineId())
				.routineKey(dto.routineKey()).createdBy(dto.createdBy()).approved(dto.approved())
				.terminal(dto.terminal()).waitingForAction(dto.waitingForAction()).build();
	}

	public static RequestJobSummaryResponseDto toResponseDto(RequestJobSummaryInternalDto dto) {
		return RequestJobSummaryResponseDto.builder().id(dto.id()).creationDate(dto.creationDate())
				.scope(dto.scope()).currentState(dto.currentState()).priority(dto.priority()).rate(dto.rate())
				.routineKey(dto.routineKey()).approved(dto.approved()).terminal(dto.terminal())
				.waitingForAction(dto.waitingForAction()).origin(dto.origin()).build();
	}

	// The context of the job is reduced to its origin relative to the asking context
	public static RequestJobResponseDto toRequestJobResponseDto(HttpJobFullDetailsInternalDto dto,
			UUID contextUuid) {
		return RequestJobResponseDto.builder().jobId(dto.jobId()).currentState(dto.currentState())
				.requestedState(dto.requestedState()).verbosity(dto.verbosity()).priority(dto.priority())
				.rate(dto.rate()).customConfig(dto.customConfig()).routineKey(dto.routineKey())
				.failureMessage(dto.failureMessage()).aiDescription(dto.aiDescription()).aiResult(dto.aiResult())
				.approved(dto.approved()).terminal(dto.currentState().isTerminal())
				.waitingForAction(waitingForAction(dto))
				.origin(JobOrigin.of(dto.createdBy(), dto.agentContextUuid(), contextUuid)).build();
	}

	private static boolean waitingForAction(HttpJobFullDetailsInternalDto dto) {
		return HttpJobUtils.isWaitingForAction(dto.currentState(), dto.createdBy(), dto.approved(),
				dto.configFileStatus());
	}

}
