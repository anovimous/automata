package com.automata.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.ClassicHttpRequest;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.impl.io.SessionOutputBufferImpl;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestWriter;
import org.springframework.data.jpa.domain.Specification;

import com.automata.common.utils.ValidationResult;
import com.automata.host.common.dto.RequestFilter;
import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.request.body.BodyParseResult;
import com.automata.request.body.BodyUtils;
import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.header.Header;
import com.automata.request.header.HeaderCompositePK;
import com.automata.request.header.HeaderUtils;
import com.automata.request.parameter.QueryParameterUtils;
import com.automata.request.parameter.QueryStringParseResult;
import com.automata.request.path.PathParsingResult;
import com.automata.request.path.PathUtils;

import jakarta.persistence.criteria.Predicate;

public abstract class RequestUtils {

	public static ValidationResult validateRequestFilter(RequestFilter filter) {
		// validate that either hostId or programId is always present
		if (filter.programId() != null || filter.hostId() != null)
			return ValidationResult.valid();
		else
			return ValidationResult.invalid();

	}

	public static Specification<Request> buildSpecification(RequestFilter filter) {

		return (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			if (filter.programId() != null) {
				predicates.add(cb.equal(root.join("program").get("id"), filter.programId()));
			}

			if (filter.hostId() != null) {
				predicates.add(cb.equal(root.join("host").get("id"), filter.hostId()));
			}

			if (filter.tenantId() != null) {
				predicates.add(cb.equal(root.join("tenant").get("id"), filter.tenantId()));
			}

			if (filter.source() != null) {
				predicates.add(cb.equal(root.get("source"), filter.source()));
			}

			if (filter.method() != null) {
				predicates.add(cb.equal(root.get("method"), filter.method()));
			}

			if (filter.computatedPath() != null && !filter.computatedPath().isBlank()) {
				predicates.add(cb.like(cb.lower(root.get("computatedPath")),
						"%" + filter.computatedPath().toLowerCase() + "%"));
			}

			if (filter.extension() != null && !filter.extension().isBlank()) {
				predicates.add(cb.equal(root.get("extension"), filter.extension()));
			}

			if (filter.contentType() != null) {
				predicates.add(cb.equal(root.get("contentType"), filter.contentType()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));

		};

	}

	public static RequestParseResult parseRawRequest(String rawRequest) {

		ByteArrayInputStream stream = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));

		SessionInputBufferImpl buffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 8192);

		DefaultHttpRequestParser parser = new DefaultHttpRequestParser();

		ClassicHttpRequest request;
		try {
			request = parser.parse(buffer, stream);
		} catch (Exception e) {
			return RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
		}

		// Here starts construction of RequestParseResult components:

		// Method
		Method customMethod = Method.valueOf(request.getMethod().toUpperCase());

		// Path and query parameters
		URI uri = URI.create(request.getPath());

		String path = uri.getPath();
		String queryString = uri.getQuery();

		PathParsingResult pathParseResult = PathUtils.parsePath(path);

		QueryStringParseResult queryStringParseResult = QueryParameterUtils.parseQueryString(queryString);

		// Version
		String version = request.getVersion().format();

		// Headers

		List<Header> headers = Arrays.asList(request.getHeaders()).stream().map((h5Header) -> {
			Header header = new Header();
			HeaderCompositePK pk = new HeaderCompositePK();
			pk.setHeader(h5Header.getName());
			header.setPrimaryKey(pk);
			return header;
		}).collect(Collectors.toList());

		// Body

		Optional<HttpEntity> optionalEntity = Optional.of(request.getEntity());

		BodyParseResult bodyParseResult;

		ContentType customContentType;

		if (optionalEntity.isPresent()) {

			String body;

			try {
				body = new String(optionalEntity.get().getContent().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				return RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
			}

			customContentType = HeaderUtils.detectContentType(
					org.apache.hc.core5.http.ContentType.parse(optionalEntity.get().getContentType()).getMimeType());

			if (customContentType == ContentType.JSON)
				bodyParseResult = BodyUtils.parseJsonBody(body);
			else if (customContentType == ContentType.FORM)
				bodyParseResult = BodyUtils.parseFormBody(body);
			else
				throw new IllegalArgumentException("No content types other than JSON and FORM body are allowed");

		} else
			bodyParseResult = new BodyParseResult();

		// Final result construction

		RequestParseResult finalParseResult = RequestParseResult.builder().method(customMethod).version(version)
				.pathParseResult(pathParseResult).queryStringParseResult(queryStringParseResult)
				.bodyParseResult(bodyParseResult).headers(headers).build();

		return finalParseResult;

	}

	public static ClassicHttpRequest composeApacheCoreRequest(RequestInternalDto internalDto, String host) {

		String method = internalDto.getMethod().toString();

		String rawPath = PathUtils.composeRawPath(internalDto.getComputatedPath(), internalDto.getPathVariableDtos());

		Optional<String> queryString = QueryParameterUtils.composeRawQueryString(internalDto.getQueryParameterDtos());

		StringBuilder stringUriBuilder = new StringBuilder().append(rawPath);

		String uri;

		if (queryString.isPresent())
			uri = stringUriBuilder.append('?').append(queryString.get()).toString();
		else
			uri = stringUriBuilder.toString();

		String version = internalDto.getVersion();

		Optional<String> rawBody = BodyUtils.composeRawBody(internalDto.getContentType(),
				internalDto.getBodyPropertyDtos());

		ClassicHttpRequest apacheRequest = new BasicClassicHttpRequest(method, URI.create(uri));

		apacheRequest.setAuthority(new URIAuthority(host));

		apacheRequest.setVersion(new ProtocolVersion("HTTP", Character.getNumericValue(version.charAt(5)),
				Character.getNumericValue(version.charAt(7))));

		rawBody.ifPresent((body) -> {

			org.apache.hc.core5.http.ContentType apacheContentType;

			if (internalDto.getContentType() == ContentType.JSON)
				apacheContentType = org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
			else
				apacheContentType = org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;

			HttpEntity bodyEntity = new StringEntity(body, apacheContentType);

			apacheRequest.setEntity(bodyEntity);
		});

		return apacheRequest;

	}

	public static String composeRawRequest(ClassicHttpRequest apacheCoreRequest) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SessionOutputBufferImpl buffer = new SessionOutputBufferImpl(8192);

		DefaultHttpRequestWriter writer = new DefaultHttpRequestWriter();

		try {

			writer.write(apacheCoreRequest, buffer, baos);

			buffer.flush(baos);

			if (apacheCoreRequest.getEntity() != null) {

				baos.write('\r');
				baos.write('\n');

				apacheCoreRequest.getEntity().writeTo(baos);
			}

		} catch (Exception e) {
			throw new RuntimeException("Error composing raw request from Apache ClassicHttpRequest");
		}

		String rawRequest = baos.toString(StandardCharsets.UTF_8);

		return rawRequest;

	}

}
