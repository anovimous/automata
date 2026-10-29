package com.automata.e2e;

import static com.automata.e2e.RawHttp.delete;
import static com.automata.e2e.RawHttp.get;
import static com.automata.e2e.RawHttp.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import com.automata.host.Host;
import com.automata.request.comparator.Comparator;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.request.comparator.Modifier;
import com.automata.request.equalityset.Equalization;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualityService;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.equalityset.RequestEqualitySetRepository;

/**
 * Requests that come in through the HTTP layer are grouped by the real comparators: what the parsing stores
 * (computed path, parameter names, body shape) decides which requests are equal.
 */
@RequestE2E
class RequestEqualityE2ETest extends RequestE2ETestBase {

	@Autowired
	RequestEqualityService serviceProxy;

	@Autowired
	RequestEqualityRelationRepository relationRepo;

	@Autowired
	RequestEqualitySetRepository setRepo;

	RequestEqualityService service;

	@BeforeEach
	void unwrapTheService() {
		// @Async would hand the call to another thread, outside the transaction of the test
		service = AopTestUtils.getTargetObject(serviceProxy);
	}

	private Modifier modifier(HashTarget target, long priority) {
		return fx.persist(Modifier.builder().target(target).description(target.name()).priority(priority).isAvailable(true).build());
	}

	private Equalization equalization(Host onHost, HashTarget... targets) {
		Comparator comparator = Comparator.builder().name("c").build();
		long priority = 1;
		for (HashTarget target : targets)
			comparator.getModifiers().add(modifier(target, priority++));
		fx.persist(comparator);
		return fx.persist(Equalization.builder().host(onHost).comparator(comparator).autoEqualize(false).build());
	}

	/** Equalizes the requests ingested so far and returns the set of each request. */
	private Map<Long, Long> equalize(HashTarget... targets) {
		Equalization equalization = equalization(host, targets);
		flushAndClear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		flushAndClear();
		return relationRepo.findAll().stream().collect(Collectors.toMap(r -> r.getRequest().getId(), r -> r.getSet().getId()));
	}

	private long id(com.automata.request.common.dto.RequestSummaryResponse summary) {
		return summary.requestId();
	}

	@Test
	void requestsOfTheSameEndpointWithDifferentIdsShareASet() throws Exception {
		long a = id(ingest(get("/users/1/orders/10")));
		long b = id(ingest(get("/users/22/orders/3000")));
		long c = id(ingest(get("/users/7/orders/8")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(sets.get(a), sets.get(b));
		assertEquals(sets.get(a), sets.get(c));
		assertEquals(1, setRepo.count());
		RequestEqualitySet set = setRepo.findAll().get(0);
		assertEquals(3, set.getNumberOfRequests());
		assertEquals("GET:/users/2@INT/orders/4@INT", set.getAlias());
	}

	@Test
	void guidsAndNumbersAreDifferentKindsOfSegment() throws Exception {
		long number = id(ingest(get("/users/1")));
		long guid = id(ingest(get("/users/550e8400-e29b-41d4-a716-446655440000")));
		long guid2 = id(ingest(get("/users/6f1c0a52-0000-4000-8000-000000000001")));
		long literal = id(ingest(get("/users/me")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(sets.get(guid), sets.get(guid2));
		assertNotEquals(sets.get(number), sets.get(guid));
		assertNotEquals(sets.get(number), sets.get(literal));
		assertNotEquals(sets.get(guid), sets.get(literal));
		assertEquals(3, setRepo.count());
	}

	@Test
	void theMethodSeparatesRequestsOfTheSamePath() throws Exception {
		long g = id(ingest(get("/users/1")));
		long d = id(ingest(delete("/users/1")));
		long p = id(ingest(post("/users/1")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(3, sets.values().stream().distinct().count());
		assertNotEquals(sets.get(g), sets.get(d));
		assertNotEquals(sets.get(g), sets.get(p));
	}

	@Test
	void theMethodIsIgnoredWhenOnlyThePathCounts() throws Exception {
		long g = id(ingest(get("/users/1")));
		long d = id(ingest(delete("/users/2")));

		Map<Long, Long> sets = equalize(HashTarget.PATH);

		assertEquals(sets.get(g), sets.get(d));
	}

	@Test
	void theQueryStringOfARequestDoesNotChangeItsPath() throws Exception {
		long a = id(ingest(get("/search?q=a")));
		long b = id(ingest(get("/search?q=b&page=2")));
		long c = id(ingest(get("/search")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(1, sets.values().stream().distinct().count());
		assertEquals(sets.get(a), sets.get(b));
		assertEquals(sets.get(a), sets.get(c));
	}

	@Test
	void parameterNamesDecideTheQueryStringAndTheirValuesDoNot() throws Exception {
		long a = id(ingest(get("/s?a=1&b=2")));
		long swapped = id(ingest(get("/s?b=9&a=8")));
		long other = id(ingest(get("/s?a=1&c=2")));
		long none = id(ingest(get("/s")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.QUERYSTRING);

		assertEquals(sets.get(a), sets.get(swapped));
		assertNotEquals(sets.get(a), sets.get(other));
		assertNotEquals(sets.get(a), sets.get(none));
	}

	@Test
	void aParameterWithoutValueCountsLikeOneWithAnEmptyValue() throws Exception {
		long a = id(ingest(get("/s?debug")));
		long b = id(ingest(get("/s?debug=")));
		long c = id(ingest(get("/s?debug=1")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.QUERYSTRING);

		assertEquals(sets.get(a), sets.get(b));
		assertEquals(sets.get(a), sets.get(c));
	}

	@Test
	void bracketsInParameterNamesAreOrdinaryCharacters() throws Exception {
		long a = id(ingest(get("/s?filter[name]=x")));
		long b = id(ingest(get("/s?filter%5Bname%5D=y")));
		long other = id(ingest(get("/s?filter[email]=x")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.QUERYSTRING);

		assertEquals(sets.get(a), sets.get(b));
		assertNotEquals(sets.get(a), sets.get(other));
	}

	@Test
	void oneParameterIsNotTheSameQueryStringAsTwoWhoseNamesConcatenateToIt() throws Exception {
		long one = id(ingest(get("/s?ab=1")));
		long two = id(ingest(get("/s?a=1&b=2")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.QUERYSTRING);

		assertNotEquals(sets.get(one), sets.get(two));
	}

	@Test
	void theShapeOfAJsonBodyDecidesTheBodyAndTheValuesDoNot() throws Exception {
		long a = id(ingest(post("/u").json("{\"name\":\"a\",\"age\":1}")));
		long sameShape = id(ingest(post("/u").json("{\"age\":99,\"name\":\"zzz\"}")));
		long moreFields = id(ingest(post("/u").json("{\"name\":\"a\",\"age\":1,\"email\":\"x\"}")));
		long fewerFields = id(ingest(post("/u").json("{\"name\":\"a\"}")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.BODY);

		assertEquals(sets.get(a), sets.get(sameShape));
		assertNotEquals(sets.get(a), sets.get(moreFields));
		assertNotEquals(sets.get(a), sets.get(fewerFields));
	}

	@Test
	void arraysOfAnyLengthHaveTheSameShape() throws Exception {
		long a = id(ingest(post("/u").json("{\"ids\":[1,2,3],\"n\":1}")));
		long b = id(ingest(post("/u").json("{\"ids\":[9],\"n\":2}")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.BODY);

		assertEquals(sets.get(a), sets.get(b));
	}

	@Test
	void aFormBodyIsComparedByTheNamesOfItsFields() throws Exception {
		long a = id(ingest(post("/l").form("user=a&pass=b")));
		long b = id(ingest(post("/l").form("pass=z&user=y")));
		long c = id(ingest(post("/l").form("user=a")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.BODY);

		assertEquals(sets.get(a), sets.get(b));
		assertNotEquals(sets.get(a), sets.get(c));
	}

	@Test
	void aRequestWithoutBodyIsNotEqualToOneWithABody() throws Exception {
		long none = id(ingest(post("/u")));
		long json = id(ingest(post("/u").json("{\"a\":1}")));
		long text = id(ingest(post("/u").contentType("text/plain").body("hello")));
		long text2 = id(ingest(post("/u").contentType("text/plain").body("world")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH, HashTarget.BODY);

		assertNotEquals(sets.get(none), sets.get(json));
		assertNotEquals(sets.get(none), sets.get(text));
		assertEquals(sets.get(text), sets.get(text2));
	}

	@Test
	void pathsAreComparedAsTheyWereSentWithoutDecodingThem() throws Exception {
		long plain = id(ingest(get("/a%41")));
		long encodedAgain = id(ingest(get("/aA")));
		long same = id(ingest(get("/a%41")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(sets.get(plain), sets.get(same));
		assertNotEquals(sets.get(plain), sets.get(encodedAgain));
	}

	@Test
	void specialCharactersInThePathMakeDifferentEndpoints() throws Exception {
		long a = id(ingest(get("/a[1]/b")));
		long b = id(ingest(get("/a{1}/b")));
		long c = id(ingest(get("/a[1]/b")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(sets.get(a), sets.get(c));
		assertNotEquals(sets.get(a), sets.get(b));
	}

	@Test
	void aPathWithAndWithoutATrailingSlashAreDifferentEndpoints() throws Exception {
		long plain = id(ingest(get("/users")));
		long slash = id(ingest(get("/users/")));
		long slash2 = id(ingest(get("/users/?x=1")));
		long root = id(ingest(get("/")));
		long root2 = id(ingest(get("/?a=1")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertNotEquals(sets.get(plain), sets.get(slash));
		assertEquals(sets.get(slash), sets.get(slash2));
		assertEquals(sets.get(root), sets.get(root2));
		assertNotEquals(sets.get(root), sets.get(plain));
		assertEquals(3, setRepo.count());
	}

	@Test
	void theVersionAndTheHeadersDoNotChangeTheEndpoint() throws Exception {
		long a = id(ingest(get("/a").header("Accept", "*/*")));
		long b = id(ingest(get("/a").version("HTTP/2").header("X-Other", "1")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(sets.get(a), sets.get(b));
	}

	@Test
	void theSameNonAsciiPathIsOneEndpoint() throws Exception {
		long a = id(ingest(get("/café")));
		long b = id(ingest(get("/café").header("X-Other", "1")));
		long other = id(ingest(get("/cafe")));

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(sets.get(a), sets.get(b));
		assertNotEquals(sets.get(a), sets.get(other));
	}

	@Test
	void requestsOfAnotherHostAreNotGrouped() throws Exception {
		long mine = id(ingest(get("/a")));
		Host other = fx.host(program, "www.example.com");
		fx.clear();
		var otherRequest = fx.request(other, com.automata.request.common.enums.Method.GET, "/a");
		fx.clear();

		Map<Long, Long> sets = equalize(HashTarget.METHOD, HashTarget.PATH);

		assertEquals(1, sets.size());
		assertNull(sets.get(otherRequest.getId()));
		assertEquals(1, sets.keySet().stream().filter(k -> k == mine).count());
	}

}
