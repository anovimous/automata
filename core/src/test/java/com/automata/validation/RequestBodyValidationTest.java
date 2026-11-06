package com.automata.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.agentcontext.AgentContextController;
import com.automata.agentcontext.AgentContextService;
import com.automata.agentcontext.AiRequestIngestionController;
import com.automata.agentcontext.AiRequestIngestionService;
import com.automata.finding.FindingController;
import com.automata.finding.FindingService;
import com.automata.host.HostController;
import com.automata.host.HostService;
import com.automata.job.api.HttpJobController;
import com.automata.job.api.HttpJobIntegrationController;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;
import com.automata.program.ProgramController;
import com.automata.program.ProgramService;
import com.automata.request.BurpAdapterService;
import com.automata.request.RequestBurpIntegrationController;
import com.automata.request.RequestController;
import com.automata.request.RequestService;
import com.automata.request.comparator.ComparatorController;
import com.automata.request.comparator.ComparatorService;
import com.automata.request.comparator.ModifierController;
import com.automata.request.comparator.ModifierService;
import com.automata.request.equalityset.RequestEqualityService;
import com.automata.response.ResponseService;
import com.automata.routine.RoutineController;
import com.automata.routine.RoutineService;
import com.automata.tenant.TenantController;
import com.automata.tenant.TenantService;
import com.automata.tenant.authentication.AuthenticationService;
import com.automata.testsupport.Mvc;
import com.automata.vulnerability.VulnerabilityController;
import com.automata.vulnerability.VulnerabilityService;

/**
 * Every body that creates something or reports something must name its required fields: a missing or blank one is
 * answered with a 400 that lists each violation, before any service is reached.
 */
class RequestBodyValidationTest {

	private final VulnerabilityService vulnerabilityService = mock(VulnerabilityService.class);
	private final ProgramService programService = mock(ProgramService.class);
	private final HostService hostService = mock(HostService.class);
	private final FindingService findingService = mock(FindingService.class);
	private final AgentContextService contextService = mock(AgentContextService.class);
	private final RoutineService routineService = mock(RoutineService.class);
	private final ComparatorService comparatorService = mock(ComparatorService.class);
	private final ModifierService modifierService = mock(ModifierService.class);
	private final RequestService requestService = mock(RequestService.class);
	private final RequestEqualityService equalityService = mock(RequestEqualityService.class);
	private final ResponseService responseService = mock(ResponseService.class);
	private final BurpAdapterService burpAdapter = mock(BurpAdapterService.class);
	private final AiRequestIngestionService aiIngestion = mock(AiRequestIngestionService.class);
	private final TenantService tenantService = mock(TenantService.class);
	private final AuthenticationService authenticationService = mock(AuthenticationService.class);
	private final HttpJobCreationService creationService = mock(HttpJobCreationService.class);
	private final HttpJobQueryService queryService = mock(HttpJobQueryService.class);
	private final HttpJobStateService stateService = mock(HttpJobStateService.class);

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new VulnerabilityController(vulnerabilityService, Mvc.mapper()), new ProgramController(programService),
				new HostController(hostService, Mvc.mapper()), new FindingController(findingService), new AgentContextController(contextService),
				new RoutineController(routineService, Mvc.mapper()), new ComparatorController(comparatorService),
				new ModifierController(modifierService), new RequestController(requestService, equalityService, responseService),
				new RequestBurpIntegrationController(burpAdapter), new AiRequestIngestionController(aiIngestion),
				new TenantController(tenantService, authenticationService),
				new HttpJobController(creationService, queryService, stateService),
				new HttpJobIntegrationController(queryService, stateService)).build();
	}

	record Rejected(String name, String url, String body, List<String> violations) {

		@Override
		public String toString() {
			return name;
		}
	}

	private static Arguments r(String name, String url, String body, String... violations) {
		return Arguments.of(new Rejected(name, url, body, List.of(violations)));
	}

	static Stream<Arguments> rejectedBodies() {
		return Stream.of(
				r("a vulnerability without a name", "/api/vulnerabilities", "{}", "name must not be blank"),
				r("a vulnerability with a blank name", "/api/vulnerabilities", "{\"name\":\"   \"}", "name must not be blank"),
				r("a program without a name", "/api/programs", "{}", "name must not be blank"),
				r("a program with a negative rate limit", "/api/programs", "{\"name\":\"x\",\"programRateLimit\":-1}", "programRateLimit must be greater than or equal to 0"),
				r("a host without anything", "/api/hosts", "{}", "host must not be blank", "scope must not be null", "hostRateLimit must not be null",
						"shortRateLimit must not be null", "longRateLimit must not be null", "programId must not be null"),
				r("a host with a blank name", "/api/hosts",
						"{\"host\":\" \",\"scope\":\"FQDN\",\"hostRateLimit\":1,\"shortRateLimit\":1,\"longRateLimit\":0,\"programId\":1}", "host must not be blank"),
				r("a tenant without anything", "/api/tenants", "{}", "name must not be blank", "email must not be blank", "hostId must not be null"),
				r("a tenant with a blank email", "/api/tenants", "{\"name\":\"a\",\"email\":\"\",\"hostId\":1}", "email must not be blank"),
				r("an authentication without data", "/api/tenants/1/authentication", "{}", "authData must not be null"),
				r("a finding without anything", "/api/findings", "{}", "requestId must not be null", "vulnerabilityId must not be null",
						"status must not be null", "evidence must not be blank", "createdBy must not be null"),
				r("a finding with a blank evidence", "/api/findings",
						"{\"requestId\":1,\"vulnerabilityId\":1,\"status\":\"SUGGESTED\",\"evidence\":\" \",\"createdBy\":\"USER\"}", "evidence must not be blank"),
				r("a review without a verdict", "/api/findings/1/review", "{\"comment\":\"c\"}", "verdict must not be null"),
				r("an agent context without anything", "/api/agent-contexts", "{}", "name must not be blank", "hostId must not be null",
						"representativeStrategy must not be null"),
				r("an item without a type", "/api/agent-contexts/1/items", "{}", "itemType must not be null"),
				r("a tenant of a context without an id", "/api/agent-contexts/1/tenants", "{}", "tenantId must not be null"),
				r("a routine without anything", "/api/routines", "{}", "key must not be blank", "protocol must not be null", "allowedTargets must not be null",
						"resultContentType must not be null"),
				r("a comparator without anything", "/api/comparators", "{}", "name must not be blank", "modifierIds must not be empty"),
				r("a comparator without modifiers", "/api/comparators", "{\"name\":\"c\",\"modifierIds\":[]}", "modifierIds must not be empty"),
				r("a modifier without anything", "/api/modifiers", "{}", "target must not be null", "description must not be blank", "priority must not be null",
						"isAvailable must not be null"),
				r("a raw request without anything", "/api/requests/raw", "{}", "requestBase64 must not be null", "source must not be null", "hostId must not be null"),
				r("a response without data", "/api/requests/1/response", "{}", "responseBase64 must not be null"),
				r("an equalization without ids", "/api/requests/equalizations", "{}", "hostId must not be null", "comparatorId must not be null"),
				r("a burp request without anything", "/api/external/requests", "{}", "requestBase64 must not be null", "host must not be blank"),
				r("an agent request without anything", "/api/external/ai/requests", "{}", "requestBase64 must not be null", "host must not be blank"),
				r("a job without anything", "/api/jobs/http", "{}", "genericDetails must not be null", "httpJobScope must not be null"),
				r("a job without its generic fields", "/api/jobs/http", "{\"httpJobScope\":\"NARROW\",\"genericDetails\":{}}",
						"genericDetails.routineId must not be null", "genericDetails.targetSelector must not be null", "genericDetails.verbosity must not be null"),
				r("a job with an empty target selector", "/api/jobs/http",
						"{\"httpJobScope\":\"NARROW\",\"genericDetails\":{\"verbosity\":\"NORMAL\",\"routineId\":1,\"targetSelector\":{}}}",
						"genericDetails.targetSelector.selector must not be null", "genericDetails.targetSelector.selectorType must not be null"),
				r("a narrow job without its details", "/api/jobs/http", jobBody("NARROW", "", ""), "narrowJobDetails is required for a NARROW job"),
				r("a wide job without its details", "/api/jobs/http", jobBody("WIDE", "", ""), "wideJobDetails is required for a WIDE job"),
				r("a narrow job with empty details", "/api/jobs/http", jobBody("NARROW", ",\"narrowJobDetails\":{}", ""),
						"narrowJobDetails.duration must not be null", "narrowJobDetails.hostId must not be null"),
				r("a wide job with empty details", "/api/jobs/http", jobBody("WIDE", "", ",\"wideJobDetails\":{}"), "wideJobDetails.programId must not be null"),
				r("a state report without a state", "/api/external/jobs/http/update/1", "{\"jobId\":1}", "newState must not be null"));
	}

	private static String jobBody(String scope, String narrow, String wide) {
		return "{\"httpJobScope\":\"" + scope + "\",\"genericDetails\":{\"verbosity\":\"NORMAL\",\"routineId\":1,\"targetSelector\":"
				+ "{\"selectorType\":\"SINGLE_HOST\",\"selector\":{\"hostId\":1}}}" + narrow + wide + "}";
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rejectedBodies")
	void aBodyWithoutItsRequiredFieldsIsRefusedWithAMessageThatNamesThem(Rejected rejected) throws Exception {
		String message = mvc.perform(post(rejected.url()).contentType(MediaType.APPLICATION_JSON).content(rejected.body()))
				.andReturn().getResponse().getContentAsString();
		int status = mvc.perform(post(rejected.url()).contentType(MediaType.APPLICATION_JSON).content(rejected.body())).andReturn()
				.getResponse().getStatus();

		assertEquals(400, status, rejected.name() + ": " + message);
		for (String violation : rejected.violations())
			assertTrue(message.contains(violation), rejected.name() + ": '" + message + "' should contain '" + violation + "'");

		verifyNoInteractions(vulnerabilityService, programService, hostService, findingService, contextService, routineService, comparatorService,
				modifierService, requestService, equalityService, responseService, burpAdapter, aiIngestion, tenantService, authenticationService,
				creationService, stateService);
	}

	@Test
	void theViolationsAreListedInOrderAndSeparated() throws Exception {
		String message = mvc.perform(post("/api/tenants").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse()
				.getContentAsString();

		assertEquals("email must not be blank; hostId must not be null; name must not be blank", message);
	}

}
