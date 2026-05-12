package com.automata.request;

import java.util.ArrayList;
import java.util.List;

import com.automata.common.utils.ValidationResult;
import com.automata.request.body.BodyParseResult;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.header.Header;
import com.automata.request.parameter.QueryStringParseResult;
import com.automata.request.path.PathParsingResult;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RequestParseResult {

	private Method method;

	private String version;

	private RequestContentType contentType;

	private PathParsingResult pathParseResult;

	private QueryStringParseResult queryStringParseResult;

	private BodyParseResult bodyParseResult;

	@Builder.Default
	private List<Header> headers = new ArrayList<>();

	private ValidationResult validationResult;
}
