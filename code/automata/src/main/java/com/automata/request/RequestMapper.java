package com.automata.request;

import java.util.List;

import org.springframework.data.domain.Page;

import com.automata.common.dto.BodyMetadata;
import com.automata.common.dto.HeaderEntry;
import com.automata.common.utils.Base64Utils;
import com.automata.common.utils.HttpMessageUtils;
import com.automata.request.common.dto.RequestLine;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.dto.RequestSummaryResponse;
import com.automata.request.common.dto.RequestSummaryResponse.RequestSummaryResponseBuilder;

public abstract class RequestMapper {

	public static Page<RequestSummaryResponse> toRequestSummaryPage(Page<Request> requests) {

		return requests.map(RequestMapper::toRequestSummaryResponse);

	}

	public static RequestSummaryResponse toRequestSummaryResponse(Request request) {

		RequestSummaryResponseBuilder builder = RequestSummaryResponse.builder();

		builder.requestId(request.getId()).method(request.getMethod()).computatedPath(request.getComputatedPath())
				.extension(request.getExtension()).version(request.getVersion())
				.numberOfProperties(request.getNumberOfProperties()).contentType(request.getContentType())
				.source(request.getSource()).hostId(request.getHost().getId()).programId(request.getProgram().getId())
				.bodyStoredAsFile(request.isBodyStoredAsFile()).bodySizeBytes(request.getBodySizeBytes());

		if (request.getTenant() != null)
			builder.tenantId(request.getTenant().getId());
		if (request.getResponse() != null)
			builder.responseId(request.getResponse().getId());

		return builder.build();

	}

	// The headers, the request target and the Content-Type are read from the stored
	// head, since only the header names are kept as columns
	public static RequestMetadataResponse toMetadataResponse(Request request, String rawHeadBase64) {

		byte[] head = Base64Utils.decodeToBytes(rawHeadBase64);

		List<HeaderEntry> headers = HttpMessageUtils.parseHeaders(head);

		RequestLine line = new RequestLine(request.getMethod(), request.getComputatedPath(), request.getExtension(),
				request.getVersion(), HttpMessageUtils.requestTarget(head));

		BodyMetadata body = new BodyMetadata(
				HttpMessageUtils.bodyStorageOf(request.getBodySizeBytes(), request.isBodyStoredAsFile()),
				HttpMessageUtils.headerValue(headers, "Content-Type"),
				request.getContentType() == null ? null : request.getContentType().name(), request.getBodySizeBytes());

		RequestMetadataResponse.RequestMetadataResponseBuilder builder = RequestMetadataResponse.builder()
				.requestId(request.getId()).hostId(request.getHost().getId())
				.programId(request.getProgram().getId()).source(request.getSource())
				.insertionDate(request.getInsertionDate()).line(line).headers(headers).body(body);

		if (request.getTenant() != null)
			builder.tenantId(request.getTenant().getId());
		if (request.getResponse() != null)
			builder.responseId(request.getResponse().getId());

		return builder.build();

	}

}
