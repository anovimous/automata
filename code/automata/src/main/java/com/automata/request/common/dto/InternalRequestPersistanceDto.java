package com.automata.request.common.dto;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.RequestParseResult;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;
import com.automata.tenant.Tenant;

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

	private RequestContentType contentType;

	private Source source;

	private RequestParseResult requestParseResult;

	private Program program;

	private Host host;

	private Tenant tenant;

}
