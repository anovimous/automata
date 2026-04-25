package com.automata.job.domain.valueobject;

import java.util.List;

import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class RequestInternalDto {

	private final Long requestId;
	private final Method method;
	private final String computatedPath;
	private final String extension;
	private final String version;
	private final int numberOfProperties;
	private final RequestContentType contentType;
	private final Source source;
	private List<PathVariableInternalDto> pathVariableDtos;
	private List<QueryParameterInternalDto> queryParameterDtos;
	private List<BodyPropertyInternalDto> bodyPropertyDtos;
}
