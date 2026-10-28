package com.automata.e2e;

import static com.automata.e2e.RawHttp.get;
import static com.automata.e2e.RawHttp.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.automata.common.dto.BodyStorage;
import com.automata.e2e.RequestCorpusE2ETest.Case;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.NarrowJobTargetRequest;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.repository.NarrowJobTargetRequestRepository;
import com.automata.job.repository.WideJobTargetHostRepository;
import com.automata.job.service.JobDataTmpStorageService;
import com.automata.request.Request;
import com.automata.request.RequestParseResult;
import com.automata.request.RequestUtils;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.path.PathVariableRepository;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;
import com.automata.testsupport.KnownBug;

/**
 * The way back: a stored request is rebuilt for a job by the real data file writer, and the rebuilt request must
 * mean what the original one meant (same method, version, path, parameters and body properties), which is
 * checked by parsing it again with the real parser.
 */
@RequestE2E
class RequestJobDataE2ETest extends RequestE2ETestBase {

	@TempDir
	Path tmp;

	@Autowired
	NarrowJobTargetRequestRepository targetRepo;

	@Autowired
	WideJobTargetHostRepository wideRepo;

	@Autowired
	PathVariableRepository pathRepo;

	@Autowired
	QueryParameterRepository queryRepo;

	@Autowired
	BodyPropertyRepository bodyRepo;

	/** The raw request that the data file of a job on this request carries. */
	private byte[] composedForAJob(long requestId) throws Exception {

		Routine routine = fx.routine("fuzz");

		HttpJobGenericDetails details = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW).currentState(JobState.DRAFT)
				.verbosity(ResultsVerbosity.NORMAL).priority(1).rate(1).build();

		NarrowHttpJob job = fx.persist(NarrowHttpJob.builder().genericDetails(details).program(program).routine(routine)
				.duration(Duration.SHORT).host(host).targetConfig(NarrowTargetConfig.builder().build()).build());

		fx.persist(NarrowJobTargetRequest.of(job, em.getEntityManager().getReference(Request.class, requestId)));

		flushAndClear();

		NarrowHttpJob reloaded = em.find(NarrowHttpJob.class, job.getId());

		JobDataTmpStorageService writer = new JobDataTmpStorageService(tmp.toString(), targetRepo, wideRepo, pathRepo, queryRepo, bodyRepo);

		String file = writer.tempStoreNarrowJobTargetData(reloaded);

		List<String> lines = Files.readAllLines(Path.of(file));

		assertEquals(1, lines.size(), "one line per target request");

		var line = om.readTree(lines.get(0));

		assertEquals(requestId, line.get("requestId").asLong());

		return Base64.getDecoder().decode(line.get("base64Request").asText());

	}

	private static String describe(byte[] raw) {
		return new String(raw, StandardCharsets.ISO_8859_1).replace("\r\n", "\\r\\n");
	}

	private static List<String> variables(RequestParseResult parsed) {
		return parsed.getPathParseResult().getPathVariables().stream().map(v -> v.getIndex() + ":" + v.getType() + "=" + v.getValue()).toList();
	}

	private static List<String> query(RequestParseResult parsed) {
		return parsed.getQueryStringParseResult().getQueryParameters().stream().map(q -> q.getParameter() + "=" + q.getValue()).toList();
	}

	private static List<String> properties(RequestParseResult parsed) {
		return parsed.getBodyParseResult().getBodyProperties().stream()
				.map(b -> b.getFullPath() + "=" + b.getValue() + ":" + b.getPropertyValueType()).toList();
	}

	/** Parsing what a job sends must give back the meaning of the request that was stored. */
	private void assertSameMeaning(String label, RawHttp original, byte[] composed) {

		RequestParseResult before = RequestUtils.parseRawRequest(original.bytes());
		RequestParseResult after = RequestUtils.parseRawRequest(composed);

		assertTrue(after.getValidationResult().isValid(), label + ": the composed request is valid: " + after.getValidationResult().getMessage()
				+ " <- " + describe(composed));
		assertEquals(before.getMethod(), after.getMethod(), label + ": method <- " + describe(composed));
		assertEquals(before.getVersion(), after.getVersion(), label + ": version <- " + describe(composed));
		assertEquals(before.getPathParseResult().getComputatedPath(), after.getPathParseResult().getComputatedPath(),
				label + ": path <- " + describe(composed));
		assertEquals(variables(before), variables(after), label + ": path variables <- " + describe(composed));
		assertEquals(query(before), query(after), label + ": query <- " + describe(composed));
		// a form body without any field (for instance "&&") has nothing to rebuild a body from, so the job sends no body
		boolean emptyForm = before.getContentType() == RequestContentType.FORM && properties(before).isEmpty();
		if (!emptyForm)
			assertEquals(before.getContentType(), after.getContentType(), label + ": content type <- " + describe(composed));
		assertEquals(properties(before), properties(after), label + ": body properties <- " + describe(composed));

		List<String> headerNames = after.getHeaders().stream().map(h -> h.getPrimaryKey().getHeader()).toList();
		assertTrue(headerNames.contains("Host"), label + ": a job request has a Host header <- " + describe(composed));
	}

	private static boolean eligible(Case c) {
		RequestContentType type = c.expected().contentType;
		return c.raw().bodyBytes().length == 0 || type == RequestContentType.JSON || type == RequestContentType.FORM;
	}

	static Stream<Arguments> everyCaseOfTheCorpus() {
		return Stream.of(RequestCorpusE2ETest.methodsAndVersions(), RequestCorpusE2ETest.framing(), RequestCorpusE2ETest.paths(),
				RequestCorpusE2ETest.queryStrings(), RequestCorpusE2ETest.headers(), RequestCorpusE2ETest.jsonBodies(),
				RequestCorpusE2ETest.formBodies(), RequestCorpusE2ETest.otherBodies(), RequestCorpusE2ETest.realisticRequests())
				.flatMap(stream -> stream);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("everyCaseOfTheCorpus")
	void aJobRebuildsWhatWasStoredOrRefusesWhatItCannotRebuild(Case c) throws Exception {

		long id = ingest(c.raw()).requestId();
		flushAndClear();

		if (!eligible(c)) {
			IneligibleJobTargetException e = assertThrows(IneligibleJobTargetException.class, () -> composedForAJob(id), c.name());
			assertTrue(e.getMessage().contains("can't be used as a job target"), c.name() + ": " + e.getMessage());
			return;
		}

		assertSameMeaning(c.name(), c.raw(), composedForAJob(id));

	}

	// ============================================================================================ what a job request looks like

	@Test
	void aJobRequestCarriesTheHostOfTheTargetAndTheContentTypeOfTheBody() throws Exception {
		long id = ingest(post("/api/users/5?active=true").header("Authorization", "Bearer x").header("X-Other", "1").json("{\"name\":\"Bob\"}")).requestId();
		flushAndClear();

		String composed = new String(composedForAJob(id), StandardCharsets.UTF_8);

		assertTrue(composed.startsWith("POST /api/users/5?active=true HTTP/1.1\r\n"), composed);
		assertTrue(composed.contains("\r\nHost: api.example.com\r\n"), composed);
		assertTrue(composed.contains("\r\nContent-Type: application/json"), composed);
		assertTrue(composed.endsWith("\r\n\r\n{\"name\":\"Bob\"}"), composed);
		// the other headers of the original request are not part of a job request: the job adds the authentication itself
		assertTrue(!composed.contains("Authorization") && !composed.contains("X-Other"), composed);
	}

	@Test
	void aGetRequestWithoutBodyHasNoContentTypeAndNoBody() throws Exception {
		long id = ingest(get("/a/b?x=1&y=2")).requestId();
		flushAndClear();

		String composed = new String(composedForAJob(id), StandardCharsets.UTF_8);

		assertEquals("GET /a/b?x=1&y=2 HTTP/1.1\r\nHost: api.example.com\r\n\r\n", composed);
	}

	@Test
	void anHttp2RequestIsRebuiltAsHttp2() throws Exception {
		long id = ingest(get("/a").version("HTTP/2")).requestId();
		flushAndClear();

		assertEquals("GET /a HTTP/2\r\nHost: api.example.com\r\n\r\n", new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	void thePathIsRebuiltExactlyAsItWasSentAndTheQueryIsEncodedAgain() throws Exception {
		long id = ingest(get("/my%20files/100%25/a[b]?q=a+b&f[x]=1|2&e=%C3%A9")).requestId();
		flushAndClear();

		assertEquals("GET /my%20files/100%25/a[b]?q=a+b&f%5Bx%5D=1%7C2&e=%C3%A9 HTTP/1.1\r\nHost: api.example.com\r\n\r\n",
				new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	void numericAndGuidSegmentsAreRestoredAtTheirPlace() throws Exception {
		String uuid = "550e8400-e29b-41d4-a716-446655440000";
		long id = ingest(get("/u/" + uuid + "/o/12/items/0007")).requestId();
		flushAndClear();

		assertEquals("GET /u/" + uuid + "/o/12/items/0007 HTTP/1.1\r\nHost: api.example.com\r\n\r\n",
				new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	void aFormBodyIsRebuiltWithEncodedFields() throws Exception {
		long id = ingest(post("/login").form("user=a+b&pass=x%26y%3Dz&empty=")).requestId();
		flushAndClear();

		String composed = new String(composedForAJob(id), StandardCharsets.UTF_8);

		assertTrue(composed.contains("Content-Type: application/x-www-form-urlencoded"), composed);
		assertTrue(composed.endsWith("\r\n\r\nuser=a+b&pass=x%26y%3Dz&empty="), composed);
	}

	@Test
	void aJsonBodyIsRebuiltWithTheOriginalStructure() throws Exception {
		long id = ingest(post("/a").json("{\"users\":[{\"id\":1,\"tags\":[\"a\",\"b\"]},{\"id\":2,\"tags\":[]}],\"meta\":{\"ok\":true,\"n\":null}}"))
				.requestId();
		flushAndClear();

		String composed = new String(composedForAJob(id), StandardCharsets.UTF_8);

		assertTrue(composed.endsWith("{\"users\":[{\"id\":1,\"tags\":[\"a\",\"b\"]},{\"id\":2,\"tags\":[]}],\"meta\":{\"ok\":true,\"n\":null}}"), composed);
	}

	// ============================================================================================ requests a job cannot rebuild

	@Test
	void aMultipartRequestIsNotRebuiltBecauseItsPartsAreNotStored() throws Exception {
		long id = ingest(post("/u").contentType("multipart/form-data; boundary=X").body("--X--")).requestId();
		flushAndClear();

		IneligibleJobTargetException e = assertThrows(IneligibleJobTargetException.class, () -> composedForAJob(id));

		assertTrue(e.getMessage().contains("MULTIPART"), e.getMessage());
	}

	@Test
	void aRequestWithABodyAndNoContentTypeIsNotRebuilt() throws Exception {
		long id = ingest(post("/a").body("a=1")).requestId();
		flushAndClear();

		IneligibleJobTargetException e = assertThrows(IneligibleJobTargetException.class, () -> composedForAJob(id));

		assertTrue(e.getMessage().contains("no Content-Type"), e.getMessage());
	}

	// ============================================================================================ known problems

	@Test
	@KnownBug("BUG-020")
	void aLiteralSegmentThatLooksLikeAPlaceholderIsRebuilt() throws Exception {
		long id = ingest(get("/docs/1@INT")).requestId();
		flushAndClear();

		assertEquals("GET /docs/1@INT HTTP/1.1\r\nHost: api.example.com\r\n\r\n", new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	void theRootPathIsRebuiltWithItsSlash() throws Exception {
		long id = ingest(get("/")).requestId();
		flushAndClear();

		assertEquals("GET / HTTP/1.1\r\nHost: api.example.com\r\n\r\n", new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	@KnownBug("BUG-019")
	void aTrailingSlashIsRebuilt() throws Exception {
		long id = ingest(get("/api/users/")).requestId();
		flushAndClear();

		assertEquals("GET /api/users/ HTTP/1.1\r\nHost: api.example.com\r\n\r\n", new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	@KnownBug("BUG-047")
	void anHttp10RequestIsRebuiltAsHttp10() throws Exception {
		long id = ingest(get("/a").version("HTTP/1.0")).requestId();
		flushAndClear();

		assertEquals("GET /a HTTP/1.0\r\nHost: api.example.com\r\n\r\n", new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	@Test
	@KnownBug("BUG-050")
	void aUtf8PathIsRebuiltAsTheSameBytes() throws Exception {
		long id = ingest(get("/café")).requestId();
		flushAndClear();

		assertEquals("GET /café HTTP/1.1\r\nHost: api.example.com\r\n\r\n", new String(composedForAJob(id), StandardCharsets.UTF_8));
	}

	// the unused method keeps the imports honest for readers who add cases
	@SuppressWarnings("unused")
	private static List<Object> unused() {
		return new ArrayList<>(List.of(Method.GET, BodyStorage.NONE));
	}

}
