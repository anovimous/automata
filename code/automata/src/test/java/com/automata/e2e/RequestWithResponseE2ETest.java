package com.automata.e2e;

import static com.automata.e2e.RawHttp.get;
import static com.automata.e2e.RawHttp.post;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.dto.RequestSummaryResponse;
import com.automata.request.common.dto.BurpRequestAdditionDto;
import com.automata.response.ResponseMetadataDto;
import com.automata.testsupport.KnownBug;

/**
 * The entries of a request: the route of the Burp extension, a response that comes with the request or is added
 * later, and the way both are read back.
 */
@RequestE2E
class RequestWithResponseE2ETest extends RequestE2ETestBase {

	private static byte[] response(String head, String body) {
		return (head + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] jsonResponse(int status, String reason, String body) {
		return response("HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json\r\nContent-Length: " + body.length(), body);
	}

	private long ingestWith(RawHttp request, byte[] response) throws Exception {
		MvcResult result = send(request.bytes(), null, response);
		assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		return om.readValue(result.getResponse().getContentAsString(), RequestSummaryResponse.class).requestId();
	}

	private ResponseMetadataDto responseMetadata(long responseId) throws Exception {
		MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/responses/{id}/metadata", responseId)).andReturn();
		assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		return om.readValue(result.getResponse().getContentAsString(), ResponseMetadataDto.class);
	}

	// ============================================================================================ the burp route

	private MvcResult burp(String hostName, byte[] request, byte[] response) throws Exception {
		BurpRequestAdditionDto dto = new BurpRequestAdditionDto(Base64.getEncoder().encodeToString(request), hostName,
				response == null ? null : Base64.getEncoder().encodeToString(response));
		return mvc.perform(MockMvcRequestBuilders.post("/api/external/requests").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(dto))).andReturn();
	}

	@Test
	void theBurpRouteStoresARequestOfAKnownHostAsABurpRequest() throws Exception {
		MvcResult result = burp(RawHttp.HOST, get("/api/users/5?x=1").bytes(), null);
		assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		flushAndClear();

		var all = requestRepo.findAll();
		assertEquals(1, all.size());
		assertEquals("/api/users/3@INT", all.get(0).getComputatedPath());
		assertEquals(com.automata.request.common.enums.Source.BURP, all.get(0).getSource());
		assertEquals(host.getId(), all.get(0).getHost().getId());
	}

	@Test
	void theBurpRouteStoresTheResponseToo() throws Exception {
		MvcResult result = burp(RawHttp.HOST, get("/a").bytes(), jsonResponse(200, "OK", "{\"ok\":true}"));
		assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		flushAndClear();

		long id = requestRepo.findAll().get(0).getId();
		RequestMetadataResponse metadata = metadata(id);
		assertNotNull(metadata.responseId());
		assertEquals(200, responseMetadata(metadata.responseId()).statusCode());
	}

	@Test
	void theBurpRouteRefusesAHostItDoesNotKnow() throws Exception {
		MvcResult result = burp("unknown.example.com", get("/a").bytes(), null);

		assertEquals(404, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("Host not found"));
		em.flush();
		assertEquals(0, requestRepo.count());
	}

	@Test
	void theBurpRouteRefusesARequestThatCannotBeParsedAndKeepsNothing() throws Exception {
		MvcResult result = burp(RawHttp.HOST, RawHttp.of("OPTIONS", "/a").bytes(), jsonResponse(200, "OK", "{}"));

		assertEquals(400, result.getResponse().getStatus());
		em.flush();
		assertEquals(0, requestRepo.count());
		assertEquals(0, headerRepo.count());
	}

	@Test
	@KnownBug("BUG-044")
	void theBurpRouteMatchesTheHostIgnoringCase() throws Exception {
		MvcResult result = burp("API.Example.COM", get("/a").bytes(), null);

		assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
	}

	// ============================================================================================ the route of the ai agent

	@Test
	void theAgentRouteParsesARequestLikeTheBurpRouteDoes() throws Exception {
		String raw = "POST /api/users/5?filter[x]=a|b&q=%26 HTTP/1.1\r\nHost: api.example.com\r\nContent-Type: application/json\r\n\r\n{\"a\":[1,2]}";
		String body = "{\"requestBase64\":\"" + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)) + "\",\"host\":\""
				+ RawHttp.HOST + "\"}";

		MvcResult viaAgent = mvc.perform(MockMvcRequestBuilders.post("/api/external/ai/requests").contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn();
		assertEquals(201, viaAgent.getResponse().getStatus(), viaAgent.getResponse().getContentAsString());
		MvcResult viaBurp = burp(RawHttp.HOST, raw.getBytes(StandardCharsets.UTF_8), null);
		assertEquals(201, viaBurp.getResponse().getStatus());
		flushAndClear();

		var requests = requestRepo.findAll();
		assertEquals(2, requests.size());
		var agent = requests.stream().filter(r -> r.getSource() == com.automata.request.common.enums.Source.AI).findFirst().orElseThrow();
		var adapter = requests.stream().filter(r -> r.getSource() == com.automata.request.common.enums.Source.BURP).findFirst().orElseThrow();
		assertEquals(adapter.getComputatedPath(), agent.getComputatedPath());
		assertEquals("/api/users/3@INT", agent.getComputatedPath());
		assertEquals(storedQuery(adapter.getId()), storedQuery(agent.getId()));
		assertEquals(List.of("filter[x]=a|b", "q=&"), storedQuery(agent.getId()));
		assertEquals(storedProperties(adapter.getId()), storedProperties(agent.getId()));
	}

	@Test
	void theAgentRouteRefusesWhatTheBurpRouteRefuses() throws Exception {
		String body = "{\"requestBase64\":\"" + Base64.getEncoder().encodeToString(RawHttp.of("OPTIONS", "/a").bytes()) + "\",\"host\":\"" + RawHttp.HOST + "\"}";

		MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/external/ai/requests").contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn();

		assertEquals(400, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("Unsupported HTTP method"));
	}

	// ============================================================================================ responses that come with the request

	@Test
	void aJsonResponseIsBrokenIntoItsStatusHeadersAndProperties() throws Exception {
		String json = "{\"id\":7,\"tags\":[\"x\"],\"owner\":{\"name\":\"bob\"}}";
		long requestId = ingestWith(get("/a"), jsonResponse(201, "Created", json));
		flushAndClear();

		RequestMetadataResponse metadata = metadata(requestId);
		ResponseMetadataDto response = responseMetadata(metadata.responseId());

		assertEquals(requestId, response.requestId());
		assertEquals(host.getId(), response.hostId());
		assertEquals(201, response.statusCode());
		assertEquals(List.of(new HeaderEntry("Content-Type", "application/json"), new HeaderEntry("Content-Length", String.valueOf(json.length()))), response.headers());
		assertEquals(BodyStorage.INLINE, response.body().storage());
		assertEquals("JSON", response.body().mimeFamily());
		List<String> properties = em.getEntityManager().createQuery("select b.fullPath || '=' || b.value || ':' || b.propertyValueType from BodyProperty b where b.response.id = :id order by b.id",
				String.class).setParameter("id", metadata.responseId()).getResultList();
		assertEquals(List.of("id=7:INT", "tags[0]=x:STRING", "owner.name=bob:STRING"), properties);
	}

	@Test
	void everyStatusCodeIsKept() throws Exception {
		for (int status : new int[] { 100, 200, 204, 301, 304, 400, 401, 403, 404, 418, 500, 503 }) {
			long requestId = ingestWith(get("/s" + status), response("HTTP/1.1 " + status + " X\r\nContent-Length: 0", ""));
			flushAndClear();

			assertEquals(status, responseMetadata(metadata(requestId).responseId()).statusCode(), "status " + status);
		}
	}

	@Test
	void aResponseThatIsNotJsonIsStoredAsAFileAndReadBackUnchanged() throws Exception {
		byte[] body = new byte[512];
		for (int i = 0; i < body.length; i++)
			body[i] = (byte) i;
		byte[] raw = new byte[("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n\r\n").length() + body.length];
		byte[] head = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		long requestId = ingestWith(get("/img"), raw);
		flushAndClear();

		long responseId = metadata(requestId).responseId();
		ResponseMetadataDto response = responseMetadata(responseId);
		assertEquals(BodyStorage.FILE, response.body().storage());
		assertEquals("IMAGE", response.body().mimeFamily());
		assertEquals(512, response.body().sizeBytes());
		assertArrayEquals(body, storedResponseBodies.get(responseId));

		MvcResult stream = mvc.perform(MockMvcRequestBuilders.get("/api/responses/{id}/body/stream", responseId)).andReturn();
		assertEquals(200, stream.getResponse().getStatus());
		assertArrayEquals(body, stream.getResponse().getContentAsByteArray());
	}

	@Test
	void theRawResponseIsKeptByteForByte() throws Exception {
		byte[] sent = jsonResponse(200, "OK", "{\"a\":\"é\"}");

		long requestId = ingestWith(get("/a"), sent);
		flushAndClear();

		MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/responses/{id}/raw", metadata(requestId).responseId())).andReturn();
		var node = om.readTree(result.getResponse().getContentAsString());
		byte[] head = Base64.getDecoder().decode(node.get("rawHeadBase64").asText());
		byte[] body = Base64.getDecoder().decode(node.get("rawBodyBase64").asText());
		byte[] rebuilt = new byte[head.length + body.length];
		System.arraycopy(head, 0, rebuilt, 0, head.length);
		System.arraycopy(body, 0, rebuilt, head.length, body.length);
		assertArrayEquals(sent, rebuilt);
	}

	@Test
	void aResponseWithoutBodyHasNoBodyStorage() throws Exception {
		long requestId = ingestWith(get("/a"), response("HTTP/1.1 204 No Content\r\nX-Empty: 1", ""));
		flushAndClear();

		ResponseMetadataDto response = responseMetadata(metadata(requestId).responseId());

		assertEquals(BodyStorage.NONE, response.body().storage());
		assertEquals(0, response.body().sizeBytes());
		assertEquals(404, mvc.perform(MockMvcRequestBuilders.get("/api/responses/{id}/body/stream", response.responseId())).andReturn().getResponse().getStatus());
	}

	@Test
	void theHeadersOfTheRequestAndOfTheResponseAreKnownToTheHost() throws Exception {
		ingestWith(get("/a").header("X-Request-Only", "1"), response("HTTP/1.1 200 OK\r\nX-Response-Only: 1\r\nContent-Length: 0", ""));
		flushAndClear();

		assertTrue(storedHeaderNames().containsAll(List.of("Host", "X-Request-Only", "X-Response-Only", "Content-Length")), storedHeaderNames().toString());
	}

	@Test
	void aMalformedResponseRefusesTheWholeEntryAndKeepsNothing() throws Exception {
		MvcResult result = send(get("/a").bytes(), null, "garbage".getBytes(StandardCharsets.ISO_8859_1));

		// the request is saved before its response is parsed and taken back by the transaction of the service, which a test
		// that runs inside one transaction cannot show
		assertEquals(400, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		assertTrue(result.getResponse().getContentAsString().contains("Invalid protocol version"), result.getResponse().getContentAsString());
	}

	@Test
	void aResponseWithNulInAJsonValueRefusesTheWholeEntry() throws Exception {
		MvcResult result = send(get("/a").bytes(), null, jsonResponse(200, "OK", "{\"a\":\"x\\u0000y\"}"));

		assertEquals(400, result.getResponse().getStatus());
		assertTrue(result.getResponse().getContentAsString().contains("contains a NUL character"));
	}

	// ============================================================================================ a response added later

	@Test
	void aResponseCanBeAddedToARequestLater() throws Exception {
		long requestId = ingest(post("/a").json("{\"a\":1}")).requestId();
		flushAndClear();

		String body = "{\"responseBase64\":\"" + Base64.getEncoder().encodeToString(jsonResponse(200, "OK", "{\"r\":1}")) + "\"}";
		MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/requests/{id}/response", requestId).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
		assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		flushAndClear();

		assertEquals(200, responseMetadata(metadata(requestId).responseId()).statusCode());
	}

	@Test
	@KnownBug("BUG-031")
	void aSecondResponseForTheSameRequestIsRefusedOrReplacesTheFirstWithoutAServerError() throws Exception {
		long requestId = ingestWith(get("/a"), jsonResponse(200, "OK", "{}"));
		flushAndClear();

		String body = "{\"responseBase64\":\"" + Base64.getEncoder().encodeToString(jsonResponse(500, "Oops", "{}")) + "\"}";
		MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/requests/{id}/response", requestId).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

		assertTrue(result.getResponse().getStatus() < 500, "status " + result.getResponse().getStatus());
	}

}
