package com.automata.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import com.automata.request.common.RequestFieldLimits;
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

		// nothing but empty lines: the parser finds no request line and gives no request
		if (request == null)
			return RequestParseResult.builder()
					.validationResult(ValidationResult.invalid("The request is empty: it has no request line")).build();

		// Here starts construction of RequestParseResult components:

		// Method
		Method customMethod;
		try {
			customMethod = Method.valueOf(request.getMethod().toUpperCase());
		} catch (IllegalArgumentException e) {
			return RequestParseResult.builder().validationResult(ValidationResult.invalid(String
					.format("Unsupported HTTP method '%s', the supported ones are %s",
							RequestFieldLimits.preview(request.getMethod()), Arrays.toString(Method.values()))))
					.build();
		}

		// Request target: the raw path and query are kept, since decoding them first
		// would break "%20", "%25" and "%26" (the query values are decoded once, as
		// parameters), and java.net.URI would refuse the characters which browsers
		// send unencoded, such as [ ] { } |
		RequestTarget target;
		try {
			target = RequestTarget.parse(request.getPath());
		} catch (IllegalArgumentException e) {
			return RequestParseResult.builder().validationResult(ValidationResult.invalid(e.getMessage())).build();
		}

		// Path Parsing:
		PathParsingResult pathParseResult = PathUtils.parsePath(target.path());

		// QueryString Parsing:
		String queryString = target.query();
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

		ValidationResult storable = validateStoredTexts(pathParseResult, queryStringParseResult, headers,
				bodyParseResult);

		if (!storable.isValid())
			return RequestParseResult.builder().validationResult(storable).build();

		// Final result construction

		RequestParseResult finalParseResult = RequestParseResult.builder().method(customMethod).version(version)
				.contentType(customContentType).pathParseResult(pathParseResult)
				.queryStringParseResult(queryStringParseResult).bodyParseResult(bodyParseResult).headers(headers)
				.rawHead(head).rawBody(body).bodyStoredAsFile(bodyStoredAsFile).bodyContentType(bodyContentType)
				.validationResult(ValidationResult.valid()).build();

		return finalParseResult;

	}

	// Each text taken from the request must fit its column and have no NUL character, otherwise the request
	// would be lost to a database error
	static ValidationResult validateStoredTexts(PathParsingResult path, QueryStringParseResult query,
			List<Header> headers, BodyParseResult body) {

		List<ValidationResult> checks = new ArrayList<>();

		checks.add(RequestFieldLimits.checkLength("The path", path.getComputatedPath(),
				RequestFieldLimits.MAX_COMPUTATED_PATH));

		checks.add(RequestFieldLimits.checkLength("The extension of the path", path.getExtension(),
				RequestFieldLimits.MAX_EXTENSION));

		path.getPathVariables().forEach(variable -> checks.add(RequestFieldLimits.checkLength(
				"A path variable", variable.getValue(), RequestFieldLimits.MAX_PATH_VARIABLE_VALUE)));

		query.getQueryParameters().forEach(parameter -> {
			checks.add(RequestFieldLimits.checkLength(
					"The name of the query parameter '" + RequestFieldLimits.preview(parameter.getParameter()) + "'",
					parameter.getParameter(), RequestFieldLimits.MAX_PARAMETER_NAME));
			checks.add(RequestFieldLimits.checkLength(
					"The value of the query parameter '" + RequestFieldLimits.preview(parameter.getParameter()) + "'",
					parameter.getValue(), RequestFieldLimits.MAX_PARAMETER_VALUE));
			checks.add(RequestFieldLimits.checkNoNul(
					"The name of the query parameter '" + RequestFieldLimits.preview(parameter.getParameter()) + "'",
					parameter.getParameter()));
			checks.add(RequestFieldLimits.checkNoNul(
					"The value of the query parameter '" + RequestFieldLimits.preview(parameter.getParameter()) + "'",
					parameter.getValue()));
		});

		headers.forEach(header -> checks.add(RequestFieldLimits.checkLength(
				"The name of the header '" + RequestFieldLimits.preview(header.getPrimaryKey().getHeader()) + "'",
				header.getPrimaryKey().getHeader(), RequestFieldLimits.MAX_HEADER_NAME)));

		body.getBodyProperties().forEach(property -> addBodyPropertyChecks(checks, property));

		return checks.stream().filter(check -> !check.isValid()).findFirst().orElse(ValidationResult.valid());

	}

	public static void addBodyPropertyChecks(List<ValidationResult> checks, com.automata.request.body.BodyProperty property) {

		String what = "the body property '" + RequestFieldLimits.preview(property.getFullPath()) + "'";

		checks.add(RequestFieldLimits.checkLength("The path of " + what, property.getFullPath(),
				RequestFieldLimits.MAX_BODY_PROPERTY_PATH));
		checks.add(RequestFieldLimits.checkNoNul("The path of " + what, property.getFullPath()));
		checks.add(RequestFieldLimits.checkNoNul("The value of " + what, property.getValue()));

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

		// the path is written as it was stored: parsing it as a URI would refuse what the sender sent
		ClassicHttpRequest apacheRequest = new BasicClassicHttpRequest(method, uri);

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
