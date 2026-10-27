package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.AopTestUtils;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.RequestUtils;
import com.automata.request.common.dto.RequestFilter;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;
import com.automata.request.comparator.Comparator;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.comparator.Modifier;
import com.automata.request.comparator.ModifierRepository;
import com.automata.request.comparator.hash.HashStrategyResolver;
import com.automata.request.comparator.hash.HashTarget;
import com.automata.request.comparator.hash.strategy.BodyHashStrategy;
import com.automata.request.comparator.hash.strategy.MethodHashStrategy;
import com.automata.request.comparator.hash.strategy.PathHashStrategy;
import com.automata.request.comparator.hash.strategy.QueryStringHashStrategy;
import com.automata.request.equalityset.Equalization;
import com.automata.request.equalityset.EqualizationRepository;
import com.automata.request.equalityset.RequestEqualityRelation;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualityService;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.tenant.Tenant;

/** The equalization with the real hash strategies, and the request queries, on a real database. */
@RepositoryIntegrationTest
@Import({ RequestEqualityService.class, HashStrategyResolver.class, MethodHashStrategy.class, PathHashStrategy.class,
		QueryStringHashStrategy.class, BodyHashStrategy.class })
class RequestEqualityIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	RequestEqualityService serviceProxy;

	@Autowired
	RequestRepository requestRepo;

	@Autowired
	ComparatorRepository comparatorRepo;

	@Autowired
	ModifierRepository modifierRepo;

	@Autowired
	EqualizationRepository equalizationRepo;

	@Autowired
	RequestEqualitySetRepository setRepo;

	@Autowired
	RequestEqualityRelationRepository relationRepo;

	Fixtures fx;

	RequestEqualityService service;

	Program program;

	Host host;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		// @Async would hand the call to another thread, outside the transaction of the test
		service = AopTestUtils.getTargetObject(serviceProxy);
		program = fx.program("Acme");
		host = fx.host(program, "api.example.com");
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

	private Map<Long, Long> setOfRequest() {
		return relationRepo.findAll().stream().collect(Collectors.toMap(r -> r.getRequest().getId(), r -> r.getSet().getId()));
	}

	@Test
	void requestsWithTheSameMethodAndPathShareASetAndOthersGetTheirOwn() {
		Request a1 = fx.request(host, Method.GET, "/users/3@INT");
		Request a2 = fx.request(host, Method.GET, "/users/3@INT");
		Request b = fx.request(host, Method.GET, "/orders");
		Request c = fx.request(host, Method.POST, "/users/3@INT");
		Equalization equalization = equalization(host, HashTarget.METHOD, HashTarget.PATH);
		fx.clear();

		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		Map<Long, Long> sets = setOfRequest();
		assertEquals(sets.get(a1.getId()), sets.get(a2.getId()));
		assertNotEquals(sets.get(a1.getId()), sets.get(b.getId()));
		assertNotEquals(sets.get(a1.getId()), sets.get(c.getId()));
		assertEquals(3, setRepo.count());
		RequestEqualitySet shared = setRepo.findById(sets.get(a1.getId())).orElseThrow();
		assertEquals(2, shared.getNumberOfRequests());
		assertEquals("GET:/users/3@INT", shared.getAlias());
		assertEquals(equalization.getId(), shared.getEqualization().getId());
		assertNotNull(equalizationRepo.findById(equalization.getId()).orElseThrow().getLastRun());
	}

	@Test
	void runningTwiceChangesNothing() {
		fx.request(host, Method.GET, "/a");
		fx.request(host, Method.GET, "/a");
		fx.request(host, Method.GET, "/b");
		Equalization equalization = equalization(host, HashTarget.METHOD, HashTarget.PATH);
		fx.clear();

		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		assertEquals(2, setRepo.count());
		assertEquals(3, relationRepo.count());
		assertEquals(List.of(1, 2), setRepo.findAll().stream().map(RequestEqualitySet::getNumberOfRequests).sorted().toList());
	}

	@Test
	void aRequestAddedLaterJoinsTheExistingSet() {
		Request first = fx.request(host, Method.GET, "/a");
		Equalization equalization = equalization(host, HashTarget.METHOD, HashTarget.PATH);
		fx.clear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		Request later = fx.request(host, Method.GET, "/a");
		Request other = fx.request(host, Method.GET, "/z");
		fx.clear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		Map<Long, Long> sets = setOfRequest();
		assertEquals(sets.get(first.getId()), sets.get(later.getId()));
		assertNotEquals(sets.get(first.getId()), sets.get(other.getId()));
		assertEquals(2, setRepo.count());
		assertEquals(2, setRepo.findById(sets.get(first.getId())).orElseThrow().getNumberOfRequests());
	}

	@Test
	void onlyTheRequestsOfTheHostOfTheEqualizationAreGrouped() {
		Host otherHost = fx.host(program, "www.example.com");
		Request mine = fx.request(host, Method.GET, "/a");
		Request theirs = fx.request(otherHost, Method.GET, "/a");
		Equalization equalization = equalization(host, HashTarget.METHOD, HashTarget.PATH);
		fx.clear();

		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		assertTrue(setOfRequest().containsKey(mine.getId()));
		assertNull(setOfRequest().get(theirs.getId()));
	}

	@Test
	void twoEqualizationsOfTheSameHostKeepTheirOwnSets() {
		Request a = fx.request(host, Method.GET, "/a");
		Request b = fx.request(host, Method.POST, "/a");
		Equalization byPath = equalization(host, HashTarget.PATH);
		Equalization byMethodAndPath = equalization(host, HashTarget.METHOD, HashTarget.PATH);
		fx.clear();

		service.equalizeRequestsIntoEqualityGroups(byPath.getId());
		service.equalizeRequestsIntoEqualityGroups(byMethodAndPath.getId());
		fx.clear();

		List<RequestEqualityRelation> relations = relationRepo.findAll();
		long inByPath = relations.stream().filter(r -> r.getSet().getEqualization().getId().equals(byPath.getId())).count();
		long inByBoth = relations.stream().filter(r -> r.getSet().getEqualization().getId().equals(byMethodAndPath.getId())).count();
		assertEquals(2, inByPath);
		assertEquals(2, inByBoth);
		assertEquals(1, setRepo.findAll().stream().filter(s -> s.getEqualization().getId().equals(byPath.getId())).count());
		assertEquals(2, setRepo.findAll().stream().filter(s -> s.getEqualization().getId().equals(byMethodAndPath.getId())).count());
		assertNotNull(a);
		assertNotNull(b);
	}

	@Test
	void theQueryStringModifierGroupsByParameterNamesNotValues() {
		Request a = fx.request(host, Method.GET, "/search");
		fx.parameter(a, "q", "cats");
		fx.parameter(a, "page", "1");
		Request b = fx.request(host, Method.GET, "/search");
		fx.parameter(b, "page", "9");
		fx.parameter(b, "q", "dogs");
		Request c = fx.request(host, Method.GET, "/search");
		fx.parameter(c, "q", "cats");
		Equalization equalization = equalization(host, HashTarget.PATH, HashTarget.QUERYSTRING);
		fx.clear();

		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		Map<Long, Long> sets = setOfRequest();
		assertEquals(sets.get(a.getId()), sets.get(b.getId()));
		assertNotEquals(sets.get(a.getId()), sets.get(c.getId()));
	}

	@Test
	void anEqualizationWithoutRequestsCreatesNoSetAndStillRecordsTheRun() {
		Equalization equalization = equalization(host, HashTarget.METHOD);
		fx.clear();

		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();

		assertEquals(0, setRepo.count());
		assertNotNull(equalizationRepo.findById(equalization.getId()).orElseThrow().getLastRun());
	}

	// ---- queries on requests ----

	@Test
	void theLatestRequestOfASetIsTheNewestOne() {
		Request older = fx.request(host, Method.GET, "/a");
		Request newer = fx.request(host, Method.GET, "/a");
		Equalization equalization = equalization(host, HashTarget.PATH);
		fx.clear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();
		RequestEqualitySet set = setRepo.findAll().get(0);

		Request top = relationRepo.findTopBySetOrderByRequestInsertionDateDesc(set);

		assertEquals(newer.getId(), top.getId());
		assertEquals(List.of(newer.getId(), older.getId()),
				relationRepo.findRequestsInSet(set.getId(), PageRequest.of(0, 10)).map(Request::getId).getContent());
		assertTrue(relationRepo.existsBySetAndRequestId(set, older.getId()));
		assertEquals(2, relationRepo.findAllBySet(set).size());
	}

	@Test
	void findInContextReturnsTheListedRequestsAndTheMembersOfTheListedSets() {
		Request listed = fx.request(host, Method.GET, "/listed");
		Request member1 = fx.request(host, Method.GET, "/member");
		Request member2 = fx.request(host, Method.GET, "/member");
		fx.request(host, Method.GET, "/unrelated");
		Equalization equalization = equalization(host, HashTarget.PATH);
		fx.clear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();
		Long memberSet = setOfRequest().get(member1.getId());

		var found = requestRepo.findInContext(List.of(listed.getId()), List.of(memberSet), PageRequest.of(0, 10));

		assertEquals(List.of(member2.getId(), member1.getId(), listed.getId()), found.map(Request::getId).getContent());
	}

	@Test
	void findSetIdsOfRequestsOnlyReportsTheGivenRequestsInTheGivenSets() {
		Request a = fx.request(host, Method.GET, "/a");
		Request b = fx.request(host, Method.GET, "/b");
		Equalization equalization = equalization(host, HashTarget.PATH);
		fx.clear();
		service.equalizeRequestsIntoEqualityGroups(equalization.getId());
		fx.clear();
		Long setOfA = setOfRequest().get(a.getId());

		var pairs = relationRepo.findSetIdsOfRequests(List.of(a.getId(), b.getId()), List.of(setOfA));

		assertEquals(1, pairs.size());
		assertEquals(a.getId(), pairs.get(0).requestId());
	}

	// ---- filter specification ----

	private List<Long> filter(RequestFilter filter) {
		return requestRepo.findAll(RequestUtils.buildSpecification(filter), PageRequest.of(0, 50, Sort.by("id"))).map(Request::getId)
				.getContent();
	}

	@Test
	void theFilterNarrowsByHostProgramMethodPathTenantAndSource() {
		Host other = fx.host(program, "www.example.com");
		Tenant tenant = fx.tenant(host, "alice");
		Request get = fx.request(host, Method.GET, "/Users/list");
		Request post = fx.request(host, Method.POST, "/orders");
		post.setTenant(tenant);
		post.setSource(Source.MANUAL);
		post.setExtension(".json");
		fx.persist(post);
		Request elsewhere = fx.request(other, Method.GET, "/Users/list");
		fx.clear();

		RequestFilter byHost = new RequestFilter();
		byHost.setHostId(host.getId());
		assertEquals(List.of(get.getId(), post.getId()), filter(byHost));

		RequestFilter byProgram = new RequestFilter();
		byProgram.setProgramId(program.getId());
		assertEquals(List.of(get.getId(), post.getId(), elsewhere.getId()), filter(byProgram));

		RequestFilter byMethod = new RequestFilter();
		byMethod.setHostId(host.getId());
		byMethod.setMethod(Method.POST);
		assertEquals(List.of(post.getId()), filter(byMethod));

		RequestFilter byPath = new RequestFilter();
		byPath.setProgramId(program.getId());
		byPath.setComputatedPath("users");
		assertEquals(List.of(get.getId(), elsewhere.getId()), filter(byPath));

		RequestFilter byTenant = new RequestFilter();
		byTenant.setHostId(host.getId());
		byTenant.setTenantId(tenant.getId());
		assertEquals(List.of(post.getId()), filter(byTenant));

		RequestFilter bySource = new RequestFilter();
		bySource.setHostId(host.getId());
		bySource.setSource(Source.MANUAL);
		assertEquals(List.of(post.getId()), filter(bySource));

		RequestFilter byExtension = new RequestFilter();
		byExtension.setHostId(host.getId());
		byExtension.setExtension(".json");
		assertEquals(List.of(post.getId()), filter(byExtension));
	}

	@Test
	void aBlankPathOrExtensionFilterIsIgnored() {
		Request a = fx.request(host, Method.GET, "/a");
		fx.clear();
		RequestFilter filter = new RequestFilter();
		filter.setHostId(host.getId());
		filter.setComputatedPath("  ");
		filter.setExtension("");

		assertEquals(List.of(a.getId()), filter(filter));
	}

	@Test
	void theTenantFilterExcludesRequestsWithoutATenant() {
		Request noTenant = fx.request(host, Method.GET, "/a");
		fx.clear();
		RequestFilter filter = new RequestFilter();
		filter.setHostId(host.getId());
		filter.setTenantId(-1L);

		assertTrue(filter(filter).isEmpty());
		assertNotNull(noTenant);
	}

	@Test
	void existsByTenantAndTenantPairsQueries() {
		Tenant tenant = fx.tenant(host, "alice");
		Tenant unused = fx.tenant(host, "bob");
		Request withTenant = fx.request(host, Method.GET, "/a");
		withTenant.setTenant(tenant);
		fx.persist(withTenant);
		Request withoutTenant = fx.request(host, Method.GET, "/b");
		fx.clear();

		assertTrue(requestRepo.existsByTenant(tenant));
		assertEquals(false, requestRepo.existsByTenant(unused));
		var pairs = requestRepo.findTenantPairsByIdIn(List.of(withTenant.getId(), withoutTenant.getId()));
		assertEquals(1, pairs.size());
		assertEquals(tenant.getId(), pairs.get(0).tenantId());
		assertEquals(2, requestRepo.countByHost(host));
		assertEquals(2, requestRepo.findIdsByHost(host).size());
	}
}
