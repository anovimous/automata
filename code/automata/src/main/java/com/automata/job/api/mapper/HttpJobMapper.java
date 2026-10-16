package com.automata.job.api.mapper;

import org.springframework.data.domain.Page;

import com.automata.job.api.dto.HttpJobResponseDto;
import com.automata.job.api.dto.HttpJobSummaryResponseDto;
import com.automata.job.domain.valueobject.HttpJobFullDetailsInternalDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;
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
				.build();
	}

	public static Page<HttpJobSummaryResponseDto> toResponseDtos(Page<HttpJobSummaryInternalDto> internalDtos) {
		return internalDtos.map(HttpJobMapper::toResponseDto);
	}

	public static HttpJobSummaryResponseDto toResponseDto(HttpJobSummaryInternalDto dto) {
		return HttpJobSummaryResponseDto.builder().id(dto.id()).creationDate(dto.creationDate()).scope(dto.scope())
				.currentState(dto.currentState()).priority(dto.priority()).rate(dto.rate()).routineId(dto.routineId())
				.routineKey(dto.routineKey()).build();
	}

}
