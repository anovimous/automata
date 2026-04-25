package com.automata.response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.impl.io.SessionOutputBufferImpl;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.springframework.data.jpa.domain.Specification;

import com.automata.common.utils.ValidationResult;
import com.automata.host.Host;
import com.automata.request.Request;
import com.automata.request.body.BodyParseResult;
import com.automata.request.body.BodyUtils;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.header.Header;
import com.automata.request.header.HeaderCompositePK;
import com.automata.request.header.HeaderUtils;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseWriter;

public abstract class ResponseUtils {

	public static ResponseParseResult parseResponse(String rawResponse) {

		ByteArrayInputStream stream = new ByteArrayInputStream(rawResponse.getBytes(StandardCharsets.ISO_8859_1));

		SessionInputBufferImpl buffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 8192);

		DefaultHttpResponseParser parser = new DefaultHttpResponseParser();

		ClassicHttpResponse response;

		try {
			response = parser.parse(buffer, stream);
		} catch (Exception e) {
			return ResponseParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
		}

		if (response.getFirstHeader(HttpHeaders.CONTENT_TYPE) != null
				&& isContentTypeParseble(response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())) {

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			// Drain internal buffer first
			int available = buffer.length();
			byte[] bufferBytes = new byte[available];
			try {
				buffer.read(bufferBytes, 0, available, stream);
				out.write(bufferBytes);
				// Then drain the stream
				out.write(stream.readAllBytes());
			} catch (IOException e) {
				ResponseParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
			}

			byte[] bodyBytes = out.toByteArray();

			if (bodyBytes.length > 0) {
				response.setEntity(new ByteArrayEntity(bodyBytes, org.apache.hc.core5.http.ContentType
						.parse(response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())));
			}
		}
		// status code

		int statusCode = response.getCode();

		// headers

		List<Header> headers = Arrays.asList(response.getHeaders()).stream().map((h5Header) -> {
			Header header = new Header();
			HeaderCompositePK pk = new HeaderCompositePK();
			pk.setHeader(h5Header.getName());
			header.setPrimaryKey(pk);
			return header;
		}).toList();

		// content length

		int contentLength;

		if (response.containsHeader(HttpHeaders.CONTENT_LENGTH))
			contentLength = Integer.parseInt(response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
		else
			contentLength = 0;

		// body and content type

		Optional<HttpEntity> optionalEntity = Optional.ofNullable(response.getEntity());

		BodyParseResult bodyParseResult;

		ResponseContentType customContentType = null;

		if (optionalEntity.isPresent()) {

			String body;

			try {
				body = new String(optionalEntity.get().getContent().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				return ResponseParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
			}

			customContentType = HeaderUtils.detectResponseContentType(
					org.apache.hc.core5.http.ContentType.parse(optionalEntity.get().getContentType()).getMimeType());

			// no if conditionals here since only JSON response is supported
			bodyParseResult = BodyUtils.parseJsonBody(body);

		} else
			bodyParseResult = new BodyParseResult();

		ResponseParseResult finalParseResult = ResponseParseResult.builder().statusCode(statusCode)
				.contentLength(contentLength).contentType(customContentType).headers(headers)
				.bodyParseResult(bodyParseResult).validationResult(ValidationResult.valid()).build();

		return finalParseResult;

	}

	public static ClassicHttpResponse composeApacheCoreResponse(ResponseInternalDto dto,
			List<BodyPropertyInternalDto> bodyProperties) {

		ClassicHttpResponse response = new BasicClassicHttpResponse(dto.statusCode());

		response.setHeader(HttpHeaders.CONTENT_LENGTH, dto.contentLength());

		// For now, only JSON body response is supported
		if (dto.contentType() != null) {

			Optional<String> rawBody = BodyUtils.composeResponseRawBody(dto.contentType(), bodyProperties);

			rawBody.ifPresent((body) -> {

				response.setHeader(HttpHeaders.CONTENT_TYPE, dto.contentType().getRaw());

				org.apache.hc.core5.http.ContentType apacheContentType = org.apache.hc.core5.http.ContentType.APPLICATION_JSON;

				HttpEntity entity = new StringEntity(rawBody.get(), apacheContentType);

				response.setEntity(entity);
			});
		}

		return response;
	}

	public static String composeRawResponse(ClassicHttpResponse apacheCoreResponse) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SessionOutputBufferImpl buffer = new SessionOutputBufferImpl(8192);

		DefaultHttpResponseWriter writer = new DefaultHttpResponseWriter();

		try {

			writer.write(apacheCoreResponse, buffer, baos);

			buffer.flush(baos);

			if (apacheCoreResponse.getEntity() != null) {
				apacheCoreResponse.getEntity().writeTo(baos);
			}

		} catch (Exception e) {
			throw new RuntimeException("Error composing raw response from Apache ClassicHttpResponse");
		}

		String rawResponse = baos.toString(StandardCharsets.UTF_8);

		return rawResponse;

	}

	public static ValidationResult validateResponseFilter(ResponseFilter filter) {

		if (filter.hostId() == null)
			return ValidationResult.invalid();
		return ValidationResult.valid();

	}

	public static Specification<Response> buildSpecification(ResponseFilter filter) {
		return (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			Join<Response, Request> request = root.join("request");
			Join<Request, Host> host = request.join("host");

			predicates.add(cb.equal(host.get("id"), filter.hostId()));

			if (filter.contentType() != null) {
				predicates.add(cb.equal(root.get("contentType"), filter.contentType()));
			}

			if (filter.contentLength() != null) {
				predicates.add(cb.equal(root.get("contentLength"), filter.contentLength()));
			}

			if (filter.statusCode() != null) {
				predicates.add(cb.equal(root.get("statusCode"), filter.statusCode()));
			}

			return cb.and(predicates.toArray(Predicate[]::new));

		};
	}

	public static boolean isContentTypeParseble(String contentType) {
//For now only JSON response bodies are stored
		return contentType.toLowerCase().equals(ResponseContentType.JSON.getRaw());

	}

}
