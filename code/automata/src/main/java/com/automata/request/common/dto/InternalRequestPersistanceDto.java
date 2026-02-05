package com.automata.request.common.dto;

import com.automata.request.RequestParseResult;
import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class InternalRequestPersistanceDto {

	private Method method;

	private String extension;

	private String version;

	private ContentType contentType;

	private Source source;

	private RequestParseResult requestParseResult;

}
