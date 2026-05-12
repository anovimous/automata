package com.automata.job.api.mapper;

import org.springframework.data.domain.Page;

import com.automata.job.api.dto.HttpJobSummaryResponseDto;
import com.automata.job.domain.valueobject.HttpJobSummaryInternalDto;

public abstract class HttpJobMapper {

	public static Page<HttpJobSummaryResponseDto> toResponseDtos(Page<HttpJobSummaryInternalDto> internalDtos) {
		return internalDtos.map(HttpJobMapper::toResponseDto);
	}

	public static HttpJobSummaryResponseDto toResponseDto(HttpJobSummaryInternalDto dto) {
		return HttpJobSummaryResponseDto.builder().id(dto.id()).creationDate(dto.creationDate()).scope(dto.scope())
				.currentState(dto.currentState()).priority(dto.priority()).rate(dto.rate()).routineId(dto.routineId())
				.routineKey(dto.routineKey()).build();
	}

}
