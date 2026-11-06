package com.automata.e2e;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;
import com.automata.host.Host;
import com.automata.integration.Fixtures;
import com.automata.program.Program;
import com.automata.agentcontext.AiRequestIngestionController;
import com.automata.request.RequestBurpIntegrationController;
import com.automata.request.RequestController;
import com.automata.request.RequestRepository;
import com.automata.request.body.BodyProperty;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.dto.RequestRawResponse;
import com.automata.request.common.dto.RequestSummaryResponse;
import com.automata.request.common.enums.Source;
import com.automata.request.header.HeaderRepository;
import com.automata.response.ResponseController;
import com.automata.request.parameter.QueryParameter;
import com.automata.request.path.PathVariable;
import com.automata.storage.BodyObjectStorage;
import com.automata.storage.StoredBody;
import com.automata.testsupport.Mvc;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sends raw requests through the real HTTP layer into the real database and reads them back through the real
 * routes, so that a test sees exactly what a client of the application sees.
 */
abstract class RequestE2ETestBase {

	@MockitoBean
	BodyObjectStorage bodyStorage;

	@Autowired
	TestEntityManager em;

	@Autowired
	RequestController requestController;

	@Autowired
	RequestBurpIntegrationController burpController;

	@Autowired
	ResponseController responseController;

	@Autowired
	AiRequestIngestionController aiController;

	@Autowired
	RequestRepository requestRepo;

	@Autowired
	HeaderRepository headerRepo;

	Fixtures fx;

	Program program;

	Host host;

	MockMvc mvc;

	final ObjectMapper om = Mvc.mapper();

	/** The bodies that were sent to the object storage, by request id. */
	final Map<Long, byte[]> storedBodies = new HashMap<>();

	final Map<Long, String> storedContentTypes = new HashMap<>();

	/** The response bodies that were sent to the object storage, by response id. */
	final Map<Long, byte[]> storedResponseBodies = new HashMap<>();

	@BeforeEach
	void setUpRequestE2E() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
		host = fx.host(program, RawHttp.HOST);
		mvc = Mvc.standalone(requestController, burpController, responseController, aiController).build();
		storedBodies.clear();
		storedContentTypes.clear();
		storedResponseBodies.clear();
		doAnswer(invocation -> {
			storedResponseBodies.put(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(bodyStorage).storeResponseBody(any(), any(), any());
		when(bodyStorage.downloadResponseBody(any())).thenAnswer(invocation -> {
			Long id = invocation.getArgument(0);
			return StoredBody.inline(storedResponseBodies.get(id), "application/octet-stream");
		});
		doAnswer(invocation -> {
			storedBodies.put(invocation.getArgument(0), invocation.getArgument(1));
			storedContentTypes.put(invocation.getArgument(0), invocation.getArgument(2));
			return null;
		}).when(bodyStorage).storeRequestBody(any(), any(), any());
		when(bodyStorage.downloadRequestBody(any())).thenAnswer(invocation -> {
			Long id = invocation.getArgument(0);
			return StoredBody.inline(storedBodies.get(id), storedContentTypes.get(id));
		});
	}

	// ---------------------------------------------------------------------------------- sending

	MvcResult send(byte[] rawRequest, Long tenantId, byte[] rawResponse) throws Exception {
		RawRequestAdditionDto dto = new RawRequestAdditionDto(Base64.getEncoder().encodeToString(rawRequest), Source.BURP,
				host.getId(), tenantId, rawResponse == null ? null : Base64.getEncoder().encodeToString(rawResponse));
		return mvc.perform(post("/api/requests/raw").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(dto)))
				.andReturn();
	}

	MvcResult send(RawHttp raw) throws Exception {
		return send(raw.bytes(), null, null);
	}

	/** Sends a request that must be accepted (201) and returns its summary. */
	RequestSummaryResponse ingest(RawHttp raw) throws Exception {
		MvcResult result = send(raw);
		assertEquals(201, result.getResponse().getStatus(), "status for " + raw.target() + ": " + result.getResponse().getContentAsString());
		return om.readValue(result.getResponse().getContentAsString(), RequestSummaryResponse.class);
	}

	void flushAndClear() {
		em.flush();
		fx.clear();
	}

	RequestMetadataResponse metadata(long requestId) throws Exception {
		MvcResult result = mvc.perform(get("/api/requests/{id}/metadata", requestId)).andReturn();
		assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		return om.readValue(result.getResponse().getContentAsString(), RequestMetadataResponse.class);
	}

	RequestRawResponse rawView(long requestId) throws Exception {
		MvcResult result = mvc.perform(get("/api/requests/{id}/raw", requestId)).andReturn();
		assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		return om.readValue(result.getResponse().getContentAsString(), RequestRawResponse.class);
	}

	// ---------------------------------------------------------------------------------- what is stored

	List<String> storedVariables(long requestId) {
		return em.getEntityManager().createQuery("select v from PathVariable v where v.request.id = :id order by v.index", PathVariable.class)
				.setParameter("id", requestId).getResultList().stream()
				.map(v -> v.getIndex() + ":" + v.getType() + "=" + v.getValue()).toList();
	}

	List<String> storedQuery(long requestId) {
		return em.getEntityManager().createQuery("select q from QueryParameter q where q.request.id = :id order by q.id", QueryParameter.class)
				.setParameter("id", requestId).getResultList().stream().map(q -> q.getParameter() + "=" + q.getValue()).toList();
	}

	List<String> storedProperties(long requestId) {
		return em.getEntityManager().createQuery("select b from BodyProperty b where b.request.id = :id order by b.id", BodyProperty.class)
				.setParameter("id", requestId).getResultList().stream()
				.map(b -> b.getFullPath() + "=" + b.getValue() + ":" + b.getPropertyValueType()).toList();
	}

	Set<String> storedHeaderNames() {
		return headerRepo.findAll().stream().map(h -> h.getPrimaryKey().getHeader()).collect(Collectors.toCollection(TreeSet::new));
	}

	// ---------------------------------------------------------------------------------- the full check

	/**
	 * Sends the request, then checks everything that can be observed: the answer, the stored parts, the metadata, the
	 * raw view (byte for byte), the body stream and the header names of the host.
	 *
	 * @param headers the headers the metadata must list; null for the ones the builder knows about
	 */
	void ingestAndVerify(String label, RawHttp raw, Expected expected, List<HeaderEntry> headers) throws Exception {

		byte[] sent = raw.bytes();
		List<HeaderEntry> expectedHeaders = headers != null ? headers : raw.expectedHeaders();
		String contentTypeHeader = expectedHeaders.stream().filter(h -> h.name().equalsIgnoreCase("Content-Type")).map(HeaderEntry::value)
				.findFirst().orElse(null);
		long bodySize = raw.bodyBytes().length;

		RequestSummaryResponse summary = ingest(raw);
		long id = summary.requestId();

		assertEquals(expected.method, summary.method(), label + ": summary method");
		assertEquals(expected.path, summary.computatedPath(), label + ": summary path");
		assertEquals(expected.extension, summary.extension(), label + ": summary extension");
		assertEquals(expected.version, summary.version(), label + ": summary version");
		assertEquals(expected.props.size(), summary.numberOfProperties(), label + ": summary number of properties");
		assertEquals(expected.contentType, summary.contentType(), label + ": summary content type");
		assertEquals(expected.storage == BodyStorage.FILE, summary.bodyStoredAsFile(), label + ": summary stored as file");
		assertEquals(bodySize, summary.bodySizeBytes(), label + ": summary body size");
		assertEquals(host.getId(), summary.hostId(), label + ": summary host");
		assertEquals(program.getId(), summary.programId(), label + ": summary program");
		assertEquals(Source.BURP, summary.source(), label + ": summary source");

		flushAndClear();

		// stored parts
		assertEquals(expected.vars, storedVariables(id), label + ": path variables");
		assertEquals(expected.query, storedQuery(id), label + ": query parameters");
		assertEquals(sorted(expected.props), sorted(storedProperties(id)), label + ": body properties");
		assertEquals(namesOf(expectedHeaders), storedHeaderNames(), label + ": header names of the host");

		// metadata view
		RequestMetadataResponse metadata = metadata(id);
		assertEquals(id, metadata.requestId(), label + ": metadata id");
		assertEquals(host.getId(), metadata.hostId(), label + ": metadata host");
		assertEquals(expected.method, metadata.line().method(), label + ": metadata method");
		assertEquals(expected.path, metadata.line().computatedPath(), label + ": metadata path");
		assertEquals(expected.extension, metadata.line().extension(), label + ": metadata extension");
		assertEquals(expected.version, metadata.line().version(), label + ": metadata version");
		assertEquals(raw.target(), metadata.line().target(), label + ": metadata target as sent");
		assertEquals(expectedHeaders, metadata.headers(), label + ": metadata headers");
		assertEquals(expected.storage, metadata.body().storage(), label + ": metadata body storage");
		assertEquals(contentTypeHeader, metadata.body().contentType(), label + ": metadata body content type header");
		assertEquals(expected.contentType == null ? null : expected.contentType.name(), metadata.body().mimeFamily(),
				label + ": metadata mime family");
		assertEquals(bodySize, metadata.body().sizeBytes(), label + ": metadata body size");
		assertNull(metadata.responseId(), label + ": metadata response");

		// raw view: the original bytes, untouched
		RequestRawResponse rawView = rawView(id);
		assertEquals(expected.storage == BodyStorage.FILE, rawView.bodyStoredAsFile(), label + ": raw view stored as file");
		ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
		rebuilt.writeBytes(Base64.getDecoder().decode(rawView.rawHeadBase64()));
		if (rawView.rawBodyBase64() != null)
			rebuilt.writeBytes(Base64.getDecoder().decode(rawView.rawBodyBase64()));
		else if (expected.storage == BodyStorage.FILE)
			rebuilt.writeBytes(storedBodies.get(id));
		assertArrayEquals(sent, rebuilt.toByteArray(), label + ": raw head + body are the bytes that were sent");
		if (expected.storage == BodyStorage.INLINE)
			assertArrayEquals(raw.bodyBytes(), Base64.getDecoder().decode(rawView.rawBodyBase64()), label + ": inline body");
		else
			assertNull(rawView.rawBodyBase64(), label + ": no inline body");

		// body stream
		MvcResult stream = mvc.perform(get("/api/requests/{id}/body/stream", id)).andReturn();
		if (expected.storage == BodyStorage.NONE) {
			assertEquals(404, stream.getResponse().getStatus(), label + ": a request without body has no stream");
		} else {
			assertEquals(200, stream.getResponse().getStatus(), label + ": stream status");
			assertArrayEquals(raw.bodyBytes(), stream.getResponse().getContentAsByteArray(), label + ": streamed body");
			assertTrue(stream.getResponse().getHeader("Content-Disposition").contains("request-" + id), label + ": attachment name");
			MediaType expectedType = mediaTypeOrOctet(contentTypeHeader);
			assertTrue(expectedType.isCompatibleWith(MediaType.parseMediaType(stream.getResponse().getContentType())),
					label + ": stream content type " + stream.getResponse().getContentType() + " vs " + contentTypeHeader);
		}
		if (expected.storage == BodyStorage.FILE) {
			assertArrayEquals(raw.bodyBytes(), storedBodies.get(id), label + ": body sent to the object storage");
			assertEquals(contentTypeHeader, storedContentTypes.get(id), label + ": content type sent to the object storage");
		} else {
			assertTrue(storedBodies.isEmpty(), label + ": nothing goes to the object storage");
		}

		assertNotNull(requestRepo.findById(id).orElse(null), label + ": the request exists");
	}

	void ingestAndVerify(String label, RawHttp raw, Expected expected) throws Exception {
		ingestAndVerify(label, raw, expected, null);
	}

	private static MediaType mediaTypeOrOctet(String contentType) {
		try {
			return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
		} catch (Exception e) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	private static List<String> sorted(List<String> values) {
		List<String> copy = new ArrayList<>(values);
		copy.sort(null);
		return copy;
	}

	private static Set<String> namesOf(List<HeaderEntry> headers) {
		return headers.stream().map(HeaderEntry::name).collect(Collectors.toCollection(TreeSet::new));
	}

	static byte[] utf8(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

}
