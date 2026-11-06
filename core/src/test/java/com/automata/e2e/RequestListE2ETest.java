package com.automata.e2e;

import static com.automata.e2e.RawHttp.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;

/** The list of requests, filtered by what the parsing stored. */
@RequestE2E
class RequestListE2ETest extends RequestE2ETestBase {

	private List<Long> idsOf(String query) throws Exception {
		MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/requests?hostId=" + host.getId() + query)).andReturn();
		assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
		JsonNode content = om.readTree(result.getResponse().getContentAsString()).get("content");
		return StreamSupport.stream(content.spliterator(), false).map(n -> n.get("requestId").asLong()).sorted().toList();
	}

	@Test
	void requestsAreFilteredByTheirExtension() throws Exception {
		long json = ingest(get("/api/users.json")).requestId();
		long json2 = ingest(get("/api/orders/5.json")).requestId();
		long minJs = ingest(get("/static/app.min.js")).requestId();
		long plainJs = ingest(get("/static/app.js")).requestId();
		long none = ingest(get("/api/users")).requestId();
		flushAndClear();

		assertEquals(List.of(json, json2), idsOf("&extension=.json").stream().sorted().toList());
		assertEquals(List.of(minJs), idsOf("&extension=.min.js"));
		assertEquals(List.of(plainJs), idsOf("&extension=.js"));
		assertEquals(List.of(), idsOf("&extension=.pdf"));
		assertEquals(5, idsOf("").size());
		assertTrue(idsOf("").contains(none));
	}

	@Test
	void theExtensionFilterIgnoresCase() throws Exception {
		long png = ingest(get("/img/LOGO.PNG")).requestId();
		flushAndClear();

		assertEquals(List.of(png), idsOf("&extension=.png"));
		assertEquals(List.of(png), idsOf("&extension=.PNG"));
		assertEquals(List.of(png), idsOf("&extension=.Png"));
	}

	@Test
	void requestsAreFilteredByTheirPath() throws Exception {
		long a = ingest(get("/api/users/1")).requestId();
		long b = ingest(get("/api/users/2")).requestId();
		ingest(get("/api/orders"));
		flushAndClear();

		assertEquals(List.of(a, b), idsOf("&computatedPath=users"));
		assertEquals(List.of(a, b), idsOf("&computatedPath=USERS"));
	}

	@Test
	void requestsAreFilteredByTheirMethod() throws Exception {
		long g = ingest(get("/a")).requestId();
		ingest(RawHttp.post("/a"));
		flushAndClear();

		assertEquals(List.of(g), idsOf("&method=GET"));
	}

}
