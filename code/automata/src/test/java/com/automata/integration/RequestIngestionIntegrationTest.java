package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.RequestRawRepository;
import com.automata.request.RequestRepository;
import com.automata.request.RequestService;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Source;
import com.automata.request.header.HeaderRepository;
import com.automata.request.header.HeaderService;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
import com.automata.request.path.PathVariableRepository;
import com.automata.response.ResponseService;
import com.automata.storage.BodyObjectStorage;
import com.automata.tenant.Tenant;
import com.automata.testsupport.KnownBug;

import jakarta.persistence.EntityNotFoundException;

/** A raw request (and response) stored through the real services and the real schema. */
@RepositoryIntegrationTest
@Import({ RequestService.class, ResponseService.class, PathService.class, QueryParameterService.class, BodyPropertyService.class,
		HeaderService.class })
class RequestIngestionIntegrationTest {

	@MockitoBean
	BodyObjectStorage bodyStorage;

	@Autowired
	TestEntityManager em;

	@Autowired
	RequestService requestService;

	@Autowired
	RequestRepository requestRepo;

	@Autowired
	RequestRawRepository rawRepo;

	@Autowired
	PathVariableRepository pathVariableRepo;

	@Autowired
	QueryParameterRepository parameterRepo;

	@Autowired
	HeaderRepository headerRepo;

	Fixtures fx;

	Program program;

	Host host;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
		host = fx.host(program, "api.example.com");
	}

	private static String b64(String message) {
		return Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.ISO_8859_1));
	}

	private RawRequestAdditionDto dto(String request, String response, Long tenantId) {
		return new RawRequestAdditionDto(b64(request), Source.BURP, host.getId(), tenantId, response == null ? null : b64(response));
	}

	private static String get(String target, String headers) {
		return "GET " + target + " HTTP/1.1\r\nHost: api.example.com\r\n" + headers + "\r\n";
	}

	@Test
	void aGetRequestIsSplitIntoItsParts() {
		Request saved = requestService.addRawRequest(dto(get("/api/users/42/orders/7?active=true&page=2", "Accept: */*\r\n"), null, null));
		fx.clear();

		Request loaded = requestRepo.findById(saved.getId()).orElseThrow();
		assertEquals(Method.GET, loaded.getMethod());
		assertEquals("/api/users/3@INT/orders/5@INT", loaded.getComputatedPath());
		assertEquals(Source.BURP, loaded.getSource());
		assertEquals(program.getId(), loaded.getProgram().getId());
		assertEquals(host.getId(), loaded.getHost().getId());
		assertEquals("HTTP/1.1", loaded.getVersion());
		assertNull(loaded.getContentType());
		assertEquals(2, pathVariableRepo.findAll().size());
		assertEquals(List.of("active", "page"),
				parameterRepo.findAll().stream().map(p -> p.getParameter()).sorted().toList());
		assertTrue(rawRepo.findById(saved.getId()).isPresent());
	}

	@Test
	void theHeaderNamesAreRememberedPerHostWithoutDuplicates() {
		requestService.addRawRequest(dto(get("/a", "Accept: */*\r\nX-Custom: 1\r\n"), null, null));
		requestService.addRawRequest(dto(get("/b", "Accept: */*\r\n"), null, null));
		fx.clear();

		assertEquals(List.of("Accept", "Host", "X-Custom"),
				headerRepo.findAll().stream().map(h -> h.getPrimaryKey().getHeader()).sorted().toList());
	}

	@Test
	void aJsonBodyIsFlattenedIntoPropertiesAndCounted() {
		String body = "{\"name\":\"bob\",\"address\":{\"city\":\"x\"},\"tags\":[1,2]}";
		String request = "POST /api/users HTTP/1.1\r\nHost: api.example.com\r\nContent-Type: application/json\r\n\r\n" + body;

		Request saved = requestService.addRawRequest(dto(request, null, null));
		fx.clear();

		Request loaded = requestRepo.findById(saved.getId()).orElseThrow();
		assertEquals(RequestContentType.JSON, loaded.getContentType());
		assertEquals(4, loaded.getNumberOfProperties());
		assertEquals(body.length(), loaded.getBodySizeBytes());
		assertEquals(4, loaded.getBodyProperties().size());
		assertEquals(false, loaded.isBodyStoredAsFile());
	}

	@Test
	void aTenantOfTheRequestIsLinked() {
		Tenant tenant = fx.tenant(host, "alice");

		Request saved = requestService.addRawRequest(dto(get("/a", ""), null, tenant.getId()));
		fx.clear();

		assertEquals(tenant.getId(), requestRepo.findById(saved.getId()).orElseThrow().getTenant().getId());
	}

	@Test
	void anUnknownTenantIsNotFoundAndNothingIsSaved() {
		assertThrows(EntityNotFoundException.class, () -> requestService.addRawRequest(dto(get("/a", ""), null, -1L)));
		assertEquals(0, requestRepo.count());
	}

	@Test
	void aRequestWithItsResponseIsStoredTogether() {
		String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 11\r\n\r\n{\"ok\":true}";

		Request saved = requestService.addRawRequestWithResponse(dto(get("/a", ""), response, null));
		fx.clear();

		var storedResponse = em.getEntityManager().createQuery("select r from Response r where r.request.id = :id", com.automata.response.Response.class)
				.setParameter("id", saved.getId()).getSingleResult();
		assertEquals(200, storedResponse.getStatusCode());
		assertEquals(1, storedResponse.getBodyProperties().size());
	}

	@Test
	void aMalformedJsonResponseRejectsTheWholeRequest() {
		String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{not json";

		assertThrows(IllegalArgumentException.class,
				() -> requestService.addRawRequestWithResponse(dto(get("/a", ""), response, null)));
	}

	@Test
	void aQueryValueOfTheLengthOfATokenIsStored() {
		String token = "x".repeat(200);

		Request saved = requestService.addRawRequest(dto(get("/a?token=" + token, ""), null, null));
		fx.clear();

		assertEquals(token, parameterRepo.findAll().get(0).getValue());
		assertNotNull(saved.getId());
	}

	@Test
	@KnownBug("BUG-021")
	void aQueryParameterWithoutValueIsStored() {
		requestService.addRawRequest(dto(get("/a?debug&x=1", ""), null, null));
		fx.clear();

		assertEquals(2, parameterRepo.count());
	}

	@Test
	@KnownBug("BUG-030")
	void aLongQueryValueOver255CharactersIsStored() {
		// JWTs and other tokens in the query are routinely longer than 255 characters
		String jwt = "e".repeat(600);

		requestService.addRawRequest(dto(get("/a?jwt=" + jwt, ""), null, null));
		fx.clear();

		assertEquals(jwt, parameterRepo.findAll().get(0).getValue());
	}

	@Test
	@KnownBug("BUG-030")
	void aLongPathOver255CharactersIsStored() {
		String path = "/" + "segment/".repeat(40) + "end";

		Request saved = requestService.addRawRequest(dto(get(path, ""), null, null));
		fx.clear();

		assertEquals(path, requestRepo.findById(saved.getId()).orElseThrow().getComputatedPath());
	}

	@Autowired
	ResponseService responseService;

	@Test
	@KnownBug("BUG-031")
	void aSecondResponseOnTheSameRequestDoesNotEndInARawDatabaseError() {
		String response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n";
		Request saved = requestService.addRawRequestWithResponse(dto(get("/a", ""), response, null));
		fx.clear();
		Request reloaded = requestRepo.findById(saved.getId()).orElseThrow();

		// the unique constraint of response.request_id answers with a DataIntegrityViolationException (HTTP 500): the
		// second response must replace the first or be refused with a clear conflict
		try {
			responseService.addResponseToRequest(b64(response), reloaded);
			em.flush();
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			throw new AssertionError("raw database error: " + e.getMessage(), e);
		} catch (RuntimeException expectedDomainError) {
			// a clear refusal is fine too
		}
	}
}
