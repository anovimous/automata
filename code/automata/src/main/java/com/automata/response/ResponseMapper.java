package com.automata.response;

import org.springframework.data.domain.Page;

import com.automata.response.ResponseDto.ResponseDtoBuilder;

public class ResponseMapper {

	public static ResponseDto toResponseDto(Response response) {

		ResponseDtoBuilder builder = ResponseDto.builder();

		builder.insertionDate(response.getInsertionDate()).statusCode(response.getStatusCode())
				.contentLength(response.getContentLength()).contentType(response.getContentType())
				.requestId(response.getRequest().getId()).build();

		return builder.build();

	}

	public static Page<ResponseDto> toResponseDtoPage(Page<Response> responses) {
		
		return responses.map(ResponseMapper::toResponseDto);
	}

}
