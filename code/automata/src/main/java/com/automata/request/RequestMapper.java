package com.automata.request;

import org.springframework.data.domain.Page;

import com.automata.request.common.dto.RequestResponse;

public abstract class RequestMapper {

	public static Page<RequestResponse> toRequestResponseList(Page<Request> requests) {
		// TODO Auto-generated method stub
		return null;
	}

	public static RequestResponse toRequestResponse(Request request) {
		// TODO Auto-generated method stub
		return null;
	}

}
