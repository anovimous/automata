package com.automata.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.ClassicHttpRequest;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.impl.io.SessionOutputBufferImpl;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParser;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestWriter;
import org.springframework.data.jpa.domain.Specification;

import com.automata.common.utils.ValidationResult;
import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.request.body.BodyParseResult;
import com.automata.request.body.BodyUtils;
import com.automata.request.common.dto.RequestFilter;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
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
		if (filter == null)
			return ValidationResult.invalid();
		// validate that either hostId or programId is always present
		if (filter.getProgramId() != null || filter.getHostId() != null)
			return ValidationResult.valid();
		else
			return ValidationResult.invalid();

	}

	public static Specification<Request> buildSpecification(RequestFilter filter) {

		return (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			if (filter.getProgramId() != null) {
				predicates.add(cb.equal(root.join("program").get("id"), filter.getProgramId()));
			}

			if (filter.getHostId() != null) {
				predicates.add(cb.equal(root.join("host").get("id"), filter.getHostId()));
			}

			if (filter.getTenantId() != null) {
				predicates.add(cb.equal(root.join("tenant").get("id"), filter.getTenantId()));
			}

			if (filter.getSource() != null) {
				predicates.add(cb.equal(root.get("source"), filter.getSource()));
			}

			if (filter.getMethod() != null) {
				predicates.add(cb.equal(root.get("method"), filter.getMethod()));
			}

			if (filter.getComputatedPath() != null && !filter.getComputatedPath().isBlank()) {
				predicates.add(cb.like(cb.lower(root.get("computatedPath")),
						"%" + filter.getComputatedPath().toLowerCase() + "%"));
			}

			if (filter.getExtension() != null && !filter.getExtension().isBlank()) {
				predicates.add(cb.equal(root.get("extension"), filter.getExtension()));
			}

			if (filter.getContentType() != null) {
				predicates.add(cb.equal(root.get("contentType"), filter.getContentType()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));

		};

	}

	public static RequestParseResult parseRawRequest(String rawRequest) {

		String version = "HTTP/1.1";

		if (rawRequest.contains("HTTP/2")) {
			version = "HTTP/2";
			// replace with 1.1 just in apache.parse since it doesn't accept HTTP/2
			// original version will still be persisted in request entity
			rawRequest = rawRequest.replaceFirst("HTTP/2", "HTTP/1.1");
		}

		ByteArrayInputStream stream = new ByteArrayInputStream(rawRequest.getBytes(StandardCharsets.UTF_8));

		SessionInputBufferImpl buffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 8192);

		DefaultHttpRequestParser parser = new DefaultHttpRequestParser();

		ClassicHttpRequest request;
		try {
			request = parser.parse(buffer, stream);
		} catch (Exception e) {
			return RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
		}

		if (request.getFirstHeader(HttpHeaders.CONTENT_TYPE) != null) {

			if (!isContentTypeParseble(request.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue()))
				return RequestParseResult.builder()
						.validationResult(ValidationResult.invalid("Content Type not supported")).build();

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
				RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
			}

			byte[] bodyBytes = out.toByteArray();

			if (bodyBytes.length > 0) {
				request.setEntity(new ByteArrayEntity(bodyBytes,
						org.apache.hc.core5.http.ContentType.parse(request.getFirstHeader("Content-Type").getValue())));
			}
		}
		// Here starts construction of RequestParseResult components:

		// Method
		Method customMethod = Method.valueOf(request.getMethod().toUpperCase());

		// URI Creation:
		URI uri = URI.create(request.getPath());

		// Path Parsing:
		String path = uri.getPath();
		PathParsingResult pathParseResult = PathUtils.parsePath(path);

		// QueryString Parsing:
		String queryString = uri.getQuery();
		QueryStringParseResult queryStringParseResult;

		if (queryString != null)
			queryStringParseResult = QueryParameterUtils.parseQueryString(queryString);
		else
			queryStringParseResult = new QueryStringParseResult();

		// Headers

		List<Header> headers = Arrays.asList(request.getHeaders()).stream().map((h5Header) -> {
			Header header = new Header();
			HeaderCompositePK pk = new HeaderCompositePK();
			pk.setHeader(h5Header.getName());
			header.setPrimaryKey(pk);
			return header;
		}).collect(Collectors.toList());

		// Body

		Optional<HttpEntity> optionalEntity = Optional.ofNullable(request.getEntity());

		BodyParseResult bodyParseResult;

		RequestContentType customContentType = null;

		if (optionalEntity.isPresent()) {

			String body;

			try {
				body = new String(optionalEntity.get().getContent().readAllBytes(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				return RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
			}

			customContentType = HeaderUtils.detectRequestContentType(
					org.apache.hc.core5.http.ContentType.parse(optionalEntity.get().getContentType()).getMimeType());

			if (customContentType == RequestContentType.JSON)
				bodyParseResult = BodyUtils.parseJsonBody(body);
			else
				bodyParseResult = BodyUtils.parseFormBody(body);

		} else
			bodyParseResult = new BodyParseResult();

		// Final result construction

		RequestParseResult finalParseResult = RequestParseResult.builder().method(customMethod).version(version)
				.contentType(customContentType).pathParseResult(pathParseResult)
				.queryStringParseResult(queryStringParseResult).bodyParseResult(bodyParseResult).headers(headers)
				.validationResult(ValidationResult.valid()).build();

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

		ClassicHttpRequest apacheRequest = new BasicClassicHttpRequest(method, URI.create(uri));

		apacheRequest.setAuthority(new URIAuthority(host));

		apacheRequest.setVersion(new ProtocolVersion("HTTP", 1, 1));

		apacheRequest.setHeader(HttpHeaders.HOST, host);

		if (internalDto.getContentType() != null) {

			Optional<String> rawBody = BodyUtils.composeRequestRawBody(internalDto.getContentType(),
					internalDto.getBodyPropertyDtos());

			rawBody.ifPresent((body) -> {

				apacheRequest.setHeader(HttpHeaders.CONTENT_TYPE, internalDto.getContentType().getRaw());

				org.apache.hc.core5.http.ContentType apacheContentType;

				if (internalDto.getContentType() == RequestContentType.JSON)
					apacheContentType = org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
				else
					apacheContentType = org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;

				HttpEntity bodyEntity = new StringEntity(body, apacheContentType);

				apacheRequest.setEntity(bodyEntity);

			});
		}
		return apacheRequest;

	}

	public static String composeRawRequest(ClassicHttpRequest apacheCoreRequest, String version) {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		SessionOutputBufferImpl buffer = new SessionOutputBufferImpl(8192);

		DefaultHttpRequestWriter writer = new DefaultHttpRequestWriter();

		try {

			writer.write(apacheCoreRequest, buffer, baos);

			buffer.flush(baos);

			if (apacheCoreRequest.getEntity() != null) {
				apacheCoreRequest.getEntity().writeTo(baos);
			}

		} catch (Exception e) {
			throw new RuntimeException("Error composing raw request from Apache ClassicHttpRequest");
		}

		String rawRequest = baos.toString(StandardCharsets.UTF_8);
		
		if (version.equals("HTTP/2"))
			rawRequest = rawRequest.replaceFirst("HTTP/1.1", "HTTP/2");

		return rawRequest;

	}

	public static boolean isContentTypeParseble(String contentType) {
		// For now only JSON and FORM request bodies are supported
		return contentType.toLowerCase().equals(RequestContentType.JSON.getRaw())
				|| contentType.toLowerCase().equals(RequestContentType.FORM.getRaw());
	}

}
