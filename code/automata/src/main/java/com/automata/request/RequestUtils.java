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
import org.apache.hc.core5.http.HttpHeaders;
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

import com.automata.common.utils.HttpMessageUtils;
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

	public static RequestParseResult parseRawRequest(byte[] rawRequest) {

		HttpMessageUtils.SplitMessage parts = HttpMessageUtils.split(rawRequest);

		byte[] head = parts.head();

		byte[] body = parts.body();

		String version = "HTTP/1.1";

		byte[] parseableHead = head;

		// HTTP/2 is only looked for in the request line, headers may contain it as text
		String headText = new String(head, StandardCharsets.ISO_8859_1);

		int firstLineEnd = headText.indexOf('\n');

		String firstLine = firstLineEnd < 0 ? headText : headText.substring(0, firstLineEnd);

		if (firstLine.contains("HTTP/2")) {
			version = "HTTP/2";
			// replace with 1.1 just in apache.parse since it doesn't accept HTTP/2
			// original version will still be persisted in request entity
			parseableHead = headText.replaceFirst("HTTP/2(\\.0)?", "HTTP/1.1").getBytes(StandardCharsets.ISO_8859_1);
		}

		SessionInputBufferImpl buffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 8192);

		DefaultHttpRequestParser parser = new DefaultHttpRequestParser();

		ClassicHttpRequest request;
		try {
			request = parser.parse(buffer, new ByteArrayInputStream(parseableHead));
		} catch (Exception e) {
			return RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
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

		// Body: detailed components are only stored for JSON and FORM bodies, a
		// MULTIPART body is stored as a file, any other body only lives in the raw

		BodyParseResult bodyParseResult = new BodyParseResult();

		RequestContentType customContentType = null;

		String bodyContentType = null;

		boolean bodyStoredAsFile = false;

		if (body.length > 0 && request.containsHeader(HttpHeaders.CONTENT_TYPE)) {

			bodyContentType = request.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();

			customContentType = HeaderUtils.detectRequestContentType(HeaderUtils.extractMimeType(bodyContentType));

			switch (customContentType) {
			case JSON -> bodyParseResult = BodyUtils.parseJsonBody(new String(body, StandardCharsets.UTF_8));
			case FORM -> bodyParseResult = BodyUtils.parseFormBody(new String(body, StandardCharsets.UTF_8));
			case MULTIPART -> bodyStoredAsFile = true;
			default -> {
			}
			}

		}

		// Final result construction

		RequestParseResult finalParseResult = RequestParseResult.builder().method(customMethod).version(version)
				.contentType(customContentType).pathParseResult(pathParseResult)
				.queryStringParseResult(queryStringParseResult).bodyParseResult(bodyParseResult).headers(headers)
				.rawHead(head).rawBody(body).bodyStoredAsFile(bodyStoredAsFile).bodyContentType(bodyContentType)
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

}
