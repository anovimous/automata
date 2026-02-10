package com.automata.response;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.springframework.data.jpa.domain.Specification;

import com.automata.common.utils.ValidationResult;
import com.automata.request.body.BodyParseResult;
import com.automata.request.body.BodyUtils;
import com.automata.request.header.Header;
import com.automata.request.header.HeaderCompositePK;
import com.automata.request.header.HeaderUtils;

import jakarta.persistence.criteria.Predicate;

import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;

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

		if (response.containsHeader("content-length"))
			contentLength = Integer.parseInt(response.getFirstHeader("content-length").getValue());
		else
			contentLength = 0;

		// body and content type

		Optional<HttpEntity> optionalEntity = Optional.of(response.getEntity());

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

			if (customContentType == ResponseContentType.JSON)
				bodyParseResult = BodyUtils.parseJsonBody(body);
			else
				throw new IllegalArgumentException("No content types other than JSON are allowed for responses");

		} else
			bodyParseResult = new BodyParseResult();

		ResponseParseResult finalParseResult = ResponseParseResult.builder().statusCode(statusCode)
				.contentLength(contentLength).contentType(customContentType).headers(headers)
				.bodyParseResult(bodyParseResult).validationResult(ValidationResult.valid()).build();

		return finalParseResult;

	}

	public static ValidationResult validateResponseFilter(ResponseFilter filter) {

		if (filter.hostId() == null)
			return ValidationResult.invalid();
		return ValidationResult.valid();

	}

	public static Specification<Response> buildSpecification(ResponseFilter filter) {
		return (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			predicates.add(cb.equal(root.join("host").get("id"), filter.hostId()));

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

}
