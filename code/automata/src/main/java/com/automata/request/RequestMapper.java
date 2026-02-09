package com.automata.request;

import org.springframework.data.domain.Page;

import com.automata.request.common.dto.RequestResponse;
import com.automata.request.common.dto.RequestResponse.RequestResponseBuilder;

public abstract class RequestMapper {

	public static Page<RequestResponse> toRequestResponseList(Page<Request> requests) {

		return requests.map(RequestMapper::toRequestResponse);

	}

	public static RequestResponse toRequestResponse(Request request) {

		RequestResponseBuilder builder = RequestResponse.builder();

		builder.method(request.getMethod()).computatedPath(request.getComputatedPath())
				.extension(request.getExtension()).version(request.getVersion())
				.numberOfProperties(request.getNumberOfProperties()).contentType(request.getContentType())
				.source(request.getSource()).hostId(request.getHost().getId()).tenantId(request.getTenant().getId())
				.programId(request.getProgram().getId());

		return builder.build();

	}

}
