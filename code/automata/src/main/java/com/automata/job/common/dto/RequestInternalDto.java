package com.automata.job.common.dto;

import java.util.List;

import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

import lombok.Data;

@Data
public class RequestInternalDto {

	private final Long requestId;
	private final Method method;
	private final String computatedPath;
	private final String extension;
	private final String version;
	private final int numberOfProperties;
	private final ContentType contentType;
	private final Source source;
	private List<PathVariableInternalDto> pathVariableDtos;
	private List<QueryParameterInternalDto> queryParameterDtos;
	private List<BodyPropertyInternalDto> bodyPropertyDtos;
}
