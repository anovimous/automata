package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.automata.export.BodyPropertyExportService;
import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.body.BodyProperty;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.PropertyValueType;
import com.automata.response.Response;
import com.automata.response.ResponseFilter;
import com.automata.response.ResponseRepository;
import com.automata.response.ResponseUtils;
import com.automata.response.ResponseContentType;

@RepositoryIntegrationTest
@Import(BodyPropertyExportService.class)
class ExportAndResponseIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	BodyPropertyExportService exportService;

	@Autowired
	BodyPropertyRepository bodyRepo;

	@Autowired
	ResponseRepository responseRepo;

	Fixtures fx;

	Program program;

	Host host;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
		host = fx.host(program, "api.example.com");
	}

	private BodyProperty property(Request request, Response response, String path, String value, PropertyValueType type) {
		BodyProperty property = BodyProperty.builder().fullPath(path).value(value).isArrayElement(false).propertyValueType(type)
				.request(request).response(response).build();
		return fx.persist(property);
	}

	private Response response(Request request, int status, ResponseContentType type, int length) {
		return fx.persist(Response.builder().request(request).statusCode(status).contentType(type).contentLength(length).build());
	}

	// ---- export ----

	@Test
	void theExportOfAHostListsTheNamesOfItsRequestsAndResponsesOnly() {
		Host other = fx.host(program, "www.example.com");
		Request mine = fx.request(host, Method.POST, "/a");
		property(mine, null, "user.email", "a@b.c", PropertyValueType.STRING);
		Response myResponse = response(mine, 200, ResponseContentType.JSON, 10);
		property(null, myResponse, "token", "t", PropertyValueType.STRING);
		Request theirs = fx.request(other, Method.POST, "/a");
		property(theirs, null, "secret", "s", PropertyValueType.STRING);
		fx.clear();

		List<String> names = exportService.export(host.getId(), null).names();

		assertEquals(Set.of("user", "email", "token"), Set.copyOf(names));
	}

	@Test
	void theExportOfAProgramCoversAllItsHosts() {
		Host other = fx.host(program, "www.example.com");
		property(fx.request(host, Method.POST, "/a"), null, "a", "1", PropertyValueType.STRING);
		property(fx.request(other, Method.POST, "/a"), null, "b", "1", PropertyValueType.STRING);
		Program foreign = fx.program("Foreign");
		Host foreignHost = fx.host(foreign, "api.foreign.test");
		property(fx.request(foreignHost, Method.POST, "/a"), null, "c", "1", PropertyValueType.STRING);
		fx.clear();

		assertEquals(Set.of("a", "b"), Set.copyOf(exportService.export(null, program.getId()).names()));
	}

	@Test
	void aPathUsedByManyRequestsIsListedOnce() {
		for (int i = 0; i < 3; i++)
			property(fx.request(host, Method.POST, "/a"), null, "same", "v" + i, PropertyValueType.STRING);
		fx.clear();

		assertEquals(List.of("same"), exportService.export(host.getId(), null).names());
	}

	@Test
	void theExportOfAnUnknownHostIsEmpty() {
		assertTrue(exportService.export(-1L, null).names().isEmpty());
	}

	// ---- body property queries used to build jobs ----

	@Test
	void bodyPropertyDtosCarryTheirOwnerAndType() {
		Request request = fx.request(host, Method.POST, "/a");
		property(request, null, "age", "30", PropertyValueType.INT);
		Response response = response(request, 200, ResponseContentType.JSON, 1);
		property(null, response, "ok", "true", PropertyValueType.BOOLEAN);
		fx.clear();

		List<BodyPropertyInternalDto> ofRequest = bodyRepo.getBodyPropertyDtosByRequestsIds(List.of(request.getId()));
		List<BodyPropertyInternalDto> ofResponse = bodyRepo.getBodyPropertyDtosByResponseIds(List.of(response.getId()));

		assertEquals(1, ofRequest.size());
		assertEquals("age", ofRequest.get(0).fullPath());
		assertEquals(PropertyValueType.INT, ofRequest.get(0).type());
		assertEquals(request.getId(), ofRequest.get(0).requestId());
		assertEquals(1, ofResponse.size());
		assertEquals(response.getId(), ofResponse.get(0).responseId());
	}

	// ---- responses ----

	@Test
	void theResponseOfARequestIsFoundByTheRequestId() {
		Request request = fx.request(host, Method.GET, "/a");
		Response response = response(request, 404, ResponseContentType.HTML, 5);
		Request without = fx.request(host, Method.GET, "/b");
		fx.clear();

		assertEquals(response.getId(), responseRepo.getByRequestId(request.getId()).orElseThrow().getId());
		assertTrue(responseRepo.getByRequestId(without.getId()).isEmpty());
		assertTrue(responseRepo.findByRequest(Request.of(request.getId())).isPresent());
	}

	@Test
	void theResponseFilterNeedsAHostAndNarrowsByStatusTypeAndLength() {
		Host other = fx.host(program, "www.example.com");
		Response ok = response(fx.request(host, Method.GET, "/a"), 200, ResponseContentType.JSON, 10);
		Response notFound = response(fx.request(host, Method.GET, "/b"), 404, ResponseContentType.HTML, 20);
		Response foreign = response(fx.request(other, Method.GET, "/a"), 200, ResponseContentType.JSON, 10);
		fx.clear();

		assertEquals(List.of(ok.getId(), notFound.getId()), ids(new ResponseFilter(null, null, null, host.getId())));
		assertEquals(List.of(notFound.getId()), ids(new ResponseFilter(404, null, null, host.getId())));
		assertEquals(List.of(ok.getId()), ids(new ResponseFilter(null, ResponseContentType.JSON, null, host.getId())));
		assertEquals(List.of(notFound.getId()), ids(new ResponseFilter(null, null, 20, host.getId())));
		assertEquals(List.of(foreign.getId()), ids(new ResponseFilter(200, ResponseContentType.JSON, 10, other.getId())));
		assertTrue(ResponseUtils.validateResponseFilter(new ResponseFilter(null, null, null, host.getId())).isValid());
		assertTrue(!ResponseUtils.validateResponseFilter(new ResponseFilter(200, null, null, null)).isValid());
	}

	private List<Long> ids(ResponseFilter filter) {
		return responseRepo.findAll(ResponseUtils.buildSpecification(filter), PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("id")))
				.map(Response::getId).getContent();
	}
}
