package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.automata.agentcontext.AgentContext;
import com.automata.agentcontext.AgentContextRepository;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.host.HostService;
import com.automata.host.common.dto.HostCreationRequest;
import com.automata.host.common.dto.PatchHostRequest;
import com.automata.host.common.enums.Scope;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantPatchRequest;
import com.automata.tenant.TenantRepository;
import com.automata.tenant.TenantService;
import com.automata.tenant.authentication.Authentication;
import com.automata.tenant.authentication.AuthNameValuePair;
import com.automata.tenant.authentication.AuthenticationRepository;
import com.automata.tenant.authentication.AuthenticationService;
import com.automata.tenant.authentication.StaticAuthData;
import com.automata.tenant.authentication.StaticData;
import com.automata.testsupport.KnownBug;

import jakarta.persistence.EntityNotFoundException;

@RepositoryIntegrationTest
@Import({ HostService.class, TenantService.class, AuthenticationService.class })
class TenantHostIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	HostRepository hostRepo;

	@Autowired
	ProgramRepository programRepo;

	@Autowired
	TenantRepository tenantRepo;

	@Autowired
	AuthenticationRepository authRepo;

	@Autowired
	AgentContextRepository contextRepo;

	@Autowired
	HostService hostService;

	@Autowired
	TenantService tenantService;

	@Autowired
	AuthenticationService authService;

	Fixtures fx;

	Program program;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
	}

	private static Host newHost(String name, Scope scope) {
		Host host = new Host();
		host.setHost(name);
		host.setScope(scope);
		return host;
	}

	private HostCreationRequest creation(String name, Long programId) {
		return new HostCreationRequest(name, Scope.FQDN, 10, 2, 3, programId);
	}

	private static StaticAuthData authData(String header) {
		StaticData data = new StaticData();
		data.getHeaders().add(new AuthNameValuePair("Authorization", header));
		return StaticAuthData.builder().data(data).build();
	}

	// ---- Host ----

	@Test
	void createHostLowercasesTheNameComputesTheLevelAndKeepsTheLimits() {
		Host created = hostService.createHost(creation("API.Example.COM", program.getId()), newHost("API.Example.COM", Scope.FQDN));
		fx.clear();

		Host loaded = hostRepo.findById(created.getId()).orElseThrow();
		assertEquals("api.example.com", loaded.getHost());
		assertEquals(1, loaded.getLevel());
		assertEquals(10, loaded.getHostRateLimit());
		assertEquals(2, loaded.getShortRateLimit());
		assertEquals(3, loaded.getLongRateLimit());
		assertEquals(program.getId(), loaded.getProgram().getId());
		assertNotNull(loaded.getInsertionDate());
		assertFalse(loaded.isOutOfScope());
	}

	@Test
	void technologiesAreStoredAsJsonAndReadBack() {
		Host host = fx.host(program, "api.example.com");
		host.setTechnologies(List.of("nginx", "spring"));
		host.setNotes("n");
		fx.persist(host);
		fx.clear();

		Host loaded = hostRepo.findById(host.getId()).orElseThrow();
		assertEquals(List.of("nginx", "spring"), loaded.getTechnologies());
		assertEquals("n", loaded.getNotes());
	}

	@Test
	void createHostOnUnknownProgramIsNotFound() {
		assertThrows(EntityNotFoundException.class,
				() -> hostService.createHost(creation("api.example.com", -1L), newHost("api.example.com", Scope.FQDN)));
	}

	@Test
	void createHostWithInvalidNameIsRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> hostService.createHost(creation("not a host", program.getId()), newHost("not a host", Scope.FQDN)));
	}

	@Test
	@KnownBug("BUG-015")
	void creatingTheSameHostTwiceIsRefusedWithAClearError() {
		hostService.createHost(creation("api.example.com", program.getId()), newHost("api.example.com", Scope.FQDN));
		em.flush();

		// today: the unique constraint on host.host surfaces as a DataIntegrityViolationException (HTTP 500)
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> hostService.createHost(creation("API.example.com", program.getId()), newHost("API.example.com", Scope.FQDN)));
		assertNotNull(ex);
	}

	@Test
	void theSameHostNameCannotBeStoredTwiceEvenInAnotherProgram() {
		Program other = fx.program("Other");
		fx.host(program, "api.example.com");

		// persisted straight through the entity manager, so the Hibernate exception is not translated
		assertThrows(org.hibernate.exception.ConstraintViolationException.class, () -> fx.host(other, "api.example.com"));
	}

	@Test
	@KnownBug("BUG-016")
	void createHostWithoutAHostNameIsRefusedAsABadRequest() {
		assertThrows(IllegalArgumentException.class,
				() -> hostService.createHost(creation(null, program.getId()), newHost(null, Scope.FQDN)));
	}

	@Test
	@KnownBug("BUG-016")
	void createHostWithoutAScopeIsRefusedAsABadRequest() {
		assertThrows(IllegalArgumentException.class,
				() -> hostService.createHost(creation("api.example.com", program.getId()), newHost("api.example.com", null)));
	}

	@Test
	void listFiltersOnTheNameIgnoringCase() {
		fx.host(program, "api.example.com");
		fx.host(program, "www.example.com");
		fx.host(program, "other.test");
		fx.clear();

		Page<Host> found = hostService.getHostsPagedAndFilteredOnQueryString("EXAMPLE", PageRequest.of(0, 10, Sort.by("host")));

		assertEquals(List.of("api.example.com", "www.example.com"), found.map(Host::getHost).getContent());
	}

	@Test
	void listOfAProgramOnlyReturnsItsOwnHosts() {
		Program other = fx.program("Other");
		fx.host(program, "api.example.com");
		fx.host(other, "api.other.test");
		fx.clear();

		Page<Host> found = hostService.getProgramHostsPagedAndFilteredOnQueryString(program.getId(), "api", PageRequest.of(0, 10));

		assertEquals(List.of("api.example.com"), found.map(Host::getHost).getContent());
	}

	@Test
	void listOfAnUnknownProgramIsNotFound() {
		assertThrows(EntityNotFoundException.class,
				() -> hostService.getProgramHostsPagedAndFilteredOnQueryString(-1L, "", PageRequest.of(0, 10)));
	}

	@Test
	void patchWithAChangedLimitIsAppliedWhenNoJobIsRunning() {
		Host host = fx.host(program, "api.example.com");
		fx.clear();

		hostService.patchHost(host.getId(), new PatchHostRequest(null, 60, 10, 10, "d", List.of("nginx"), "n"));
		fx.clear();

		Host loaded = hostRepo.findById(host.getId()).orElseThrow();
		assertEquals(60, loaded.getHostRateLimit());
		assertEquals(10, loaded.getShortRateLimit());
		assertEquals("d", loaded.getDescription());
		assertEquals(List.of("nginx"), loaded.getTechnologies());
	}

	@Test
	void rateLimitQueriesReturnTheStoredLimits() {
		Host host = fx.host(program, "api.example.com");
		fx.clear();

		var all = hostRepo.findRateLimitsAllDtoById(host.getId());
		var some = hostRepo.findDtosByIds(java.util.Set.of(host.getId(), -5L));

		assertEquals(100, all.rateLimit());
		assertEquals(20, all.shortRateLimit());
		assertEquals(20, all.longRateLimit());
		assertEquals(1, some.size());
		assertEquals(host.getId(), some.get(0).hostId());
	}

	@Test
	void anyInScopeProgramHostHasRateLimitLessThanGivenRateIgnoresOutOfScopeHosts() {
		Host closed = fx.host(program, "closed.example.com");
		closed.setHostRateLimit(0);
		closed.setOutOfScope(true);
		fx.persist(closed);
		fx.host(program, "api.example.com"); // limit 100
		fx.clear();

		assertFalse(hostRepo.anyInScopeProgramHostHasRateLimitLessThanGivenRate(program.getId(), 50));
		assertTrue(hostRepo.anyInScopeProgramHostHasRateLimitLessThanGivenRate(program.getId(), 101));
	}

	@Test
	void findByHostFindsOnlyAnExactName() {
		fx.host(program, "api.example.com");
		fx.clear();

		assertTrue(hostRepo.findByHost("api.example.com").isPresent());
		assertTrue(hostRepo.findByHost("api.example").isEmpty());
	}

	// ---- Tenant ----

	@Test
	void createTenantPersistsItUnderTheHost() {
		Host host = fx.host(program, "api.example.com");
		Tenant created = tenantService.createTenant(Tenant.builder().name("alice").email("a@x.test").build(), host.getId());
		fx.clear();

		Tenant loaded = tenantService.getTenantById(created.getId());
		assertEquals(host.getId(), loaded.getHost().getId());
		assertNotNull(loaded.getCreationDate());
		assertNull(loaded.getAuthentication());
	}

	@Test
	void tenantWithoutEmailIsRejectedByTheDatabase() {
		Host host = fx.host(program, "api.example.com");
		Tenant tenant = Tenant.builder().name("alice").host(host).build();

		assertThrows(DataIntegrityViolationException.class, () -> tenantRepo.saveAndFlush(tenant));
	}

	@Test
	void tenantListIsPerHostAndPaged() {
		Host host = fx.host(program, "api.example.com");
		Host other = fx.host(program, "www.example.com");
		fx.tenant(host, "a");
		fx.tenant(host, "b");
		fx.tenant(host, "c");
		fx.tenant(other, "z");
		fx.clear();

		Page<Tenant> first = tenantService.getHostTenantsPage(host.getId(), PageRequest.of(0, 2, Sort.by("name")));
		Page<Tenant> second = tenantService.getHostTenantsPage(host.getId(), PageRequest.of(1, 2, Sort.by("name")));

		assertEquals(3, first.getTotalElements());
		assertEquals(List.of("a", "b"), first.map(Tenant::getName).getContent());
		assertEquals(List.of("c"), second.map(Tenant::getName).getContent());
	}

	@Test
	void patchTenantPersists() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		fx.clear();

		tenantService.patchTenant(tenant.getId(), new TenantPatchRequest(Optional.of("alicia"), Optional.of("n@x.test")));
		fx.clear();

		Tenant loaded = tenantRepo.findById(tenant.getId()).orElseThrow();
		assertEquals("alicia", loaded.getName());
		assertEquals("n@x.test", loaded.getEmail());
	}

	@Test
	void deleteOfAFreeTenantRemovesIt() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		fx.clear();

		tenantService.deleteTenant(tenant.getId());
		fx.clear();

		assertTrue(tenantRepo.findById(tenant.getId()).isEmpty());
	}

	@Test
	void deleteOfATenantInAnAgentContextIsRefused() {
		Host host = fx.host(program, "api.example.com");
		Tenant tenant = fx.tenant(host, "alice");
		AgentContext context = AgentContext.builder().host(host).name("ctx").maxRate(5)
				.representativeStrategy(com.automata.agentcontext.common.enums.RepresentativeStrategy.LATEST).build();
		context.getTenants().add(tenant);
		fx.persist(context);
		fx.clear();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> tenantService.deleteTenant(tenant.getId()));
		assertTrue(ex.getMessage().contains("agent context"));
		assertTrue(contextRepo.existsByTenantsId(tenant.getId()));
	}

	@Test
	@KnownBug("BUG-017")
	void aTenantWithAnAuthenticationCanBeDeletedAndTakesItsAuthenticationWithIt() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		Authentication authentication = authService.createAuthentication(Authentication.builder().authData(authData("Bearer a")).build(),
				tenant.getId());
		em.flush();
		Long authId = authentication.getId();
		fx.clear();

		tenantService.deleteTenant(tenant.getId());
		fx.clear();

		assertTrue(authRepo.findById(authId).isEmpty());
	}

	// ---- Authentication ----

	@Test
	void authenticationDataRoundTripsThroughTheJsonColumn() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		authService.createAuthentication(Authentication.builder().authData(authData("Bearer a")).build(), tenant.getId());
		fx.clear();

		Authentication loaded = authService.getAuthenticationOfTenant(tenant.getId());

		assertEquals("Bearer a", loaded.getAuthData().getData().getHeaders().get(0).getValue());
		assertTrue(loaded.getAuthData().getData().getCookies().isEmpty());
		assertNotNull(loaded.getCreationDate());
	}

	@Test
	void creatingAuthenticationAgainReplacesTheOldOne() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		Authentication first = authService.createAuthentication(Authentication.builder().authData(authData("Bearer a")).build(), tenant.getId());
		em.flush();
		Long firstId = first.getId();
		fx.clear();

		authService.createAuthentication(Authentication.builder().authData(authData("Bearer b")).build(), tenant.getId());
		fx.clear();

		assertEquals("Bearer b", authService.getAuthenticationOfTenant(tenant.getId()).getAuthData().getData().getHeaders().get(0).getValue());
		assertTrue(authRepo.findById(firstId).isEmpty());
		assertEquals(1, authRepo.count());
	}

	@Test
	void patchAuthenticationReplacesTheData() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		authService.createAuthentication(Authentication.builder().authData(authData("Bearer a")).build(), tenant.getId());
		fx.clear();

		authService.patchAuthentication(new com.automata.tenant.authentication.AuthenticationPatchRequest(Optional.of(authData("Bearer z"))),
				tenant.getId());
		fx.clear();

		assertEquals("Bearer z", authService.getAuthenticationOfTenant(tenant.getId()).getAuthData().getData().getHeaders().get(0).getValue());
	}

	@Test
	@KnownBug("BUG-010")
	void deleteAuthenticationRemovesItFromTheTenant() {
		Tenant tenant = fx.tenant(fx.host(program, "api.example.com"), "alice");
		authService.createAuthentication(Authentication.builder().authData(authData("Bearer a")).build(), tenant.getId());
		fx.clear();

		authService.deleteAuthentication(tenant.getId());
		fx.clear();

		assertNull(authService.getAuthenticationOfTenant(tenant.getId()));
		assertEquals(0, authRepo.count());
	}
}
