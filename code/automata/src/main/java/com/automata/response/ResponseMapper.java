package com.automata.response;

import java.util.List;

import org.springframework.data.domain.Page;

import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.HeaderEntry;
import com.automata.common.utils.Base64Utils;
import com.automata.common.utils.HttpMessageUtils;
import com.automata.response.ResponseSummaryDto.ResponseSummaryDtoBuilder;

public class ResponseMapper {

	public static ResponseSummaryDto toSummaryDto(Response response) {

		ResponseSummaryDtoBuilder builder = ResponseSummaryDto.builder();

		builder.responseId(response.getId()).insertionDate(response.getInsertionDate())
				.statusCode(response.getStatusCode()).contentLength(response.getContentLength())
				.contentType(response.getContentType()).requestId(response.getRequest().getId())
				.hostId(response.getRequest().getHost().getId()).bodyStoredAsFile(response.isBodyStoredAsFile())
				.bodySizeBytes(response.getBodySizeBytes());

		return builder.build();

	}

	public static Page<ResponseSummaryDto> toSummaryDtoPage(Page<Response> responses) {

		return responses.map(ResponseMapper::toSummaryDto);
	}

	// The headers and the Content-Type are read from the stored head, since only the
	// header names are kept as columns
	public static ResponseMetadataDto toMetadataDto(Response response, String rawHeadBase64) {

		List<HeaderEntry> headers = HttpMessageUtils.parseHeaders(Base64Utils.decodeToBytes(rawHeadBase64));

		BodyMetadata body = new BodyMetadata(
				HttpMessageUtils.bodyStorageOf(response.getBodySizeBytes(), response.isBodyStoredAsFile()),
				HttpMessageUtils.headerValue(headers, "Content-Type"),
				response.getContentType() == null ? null : response.getContentType().name(),
				response.getBodySizeBytes());

		return ResponseMetadataDto.builder().responseId(response.getId()).requestId(response.getRequest().getId())
				.hostId(response.getRequest().getHost().getId()).statusCode(response.getStatusCode())
				.insertionDate(response.getInsertionDate()).headers(headers).body(body).build();

	}

}
