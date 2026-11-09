package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import com.automata.GlobalExceptionHandler;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.host.common.enums.Scope;
import com.automata.program.Program;
import com.automata.program.ProgramRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.Tenant;
import com.automata.tenant.TenantRepository;

/**
 * The refusals of the real PostgreSQL, as the application answers them: the handler tells the constraints apart by
 * the SQL state, which is only known for sure with the real driver.
 */
@RepositoryIntegrationTest
class DatabaseErrorMappingIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	ProgramRepository programRepo;

	@Autowired
	HostRepository hostRepo;

	@Autowired
	RoutineRepository routineRepo;

	@Autowired
	TenantRepository tenantRepo;

	@Autowired
	RequestRepository requestRepo;

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbc;

	final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	Fixtures fx;

	Program program;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
		program = fx.program("Acme");
	}

	private ResponseEntity<String> answerTo(DataIntegrityViolationException e) {
		return handler.handleDataIntegrityViolation(e);
	}

	@Test
	void aDuplicateUniqueValueAnswers409() {
		fx.routine("fuzz");
		em.flush();
		Routine duplicate = new Routine();
		duplicate.setKey("fuzz");
		duplicate.setDescription("d");
		duplicate.setUsageDescriptionForAgent("u");
		duplicate.setResultContentType(com.automata.routine.common.enums.ResultContentType.TEXT);
		duplicate.setAllowedTargets(java.util.Set.of(com.automata.routine.common.enums.TargetCardinalityPair.SINGLE_HOST));

		DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, () -> routineRepo.saveAndFlush(duplicate));

		ResponseEntity<String> answer = answerTo(e);
		assertEquals(409, answer.getStatusCode().value());
		assertEquals("A record with the same unique value already exists", answer.getBody());
	}

	@Test
	void deletingARecordThatIsStillUsedAnswers409() {
		fx.host(program, "api.example.com");
		em.flush();

		DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update("delete from program where id = ?", program.getId()));

		ResponseEntity<String> answer = answerTo(e);
		assertEquals(409, answer.getStatusCode().value());
		assertEquals("The change conflicts with related records: the record is still used by other records, or refers to one that does not exist",
				answer.getBody());
	}

	@Test
	void aMissingRequiredValueAnswers400() {
		Host host = fx.host(program, "api.example.com");
		Tenant tenant = Tenant.builder().host(host).email("e@example.com").build();

		DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, () -> tenantRepo.saveAndFlush(tenant));

		ResponseEntity<String> answer = answerTo(e);
		assertEquals(400, answer.getStatusCode().value());
		assertEquals("A required value is missing", answer.getBody());
	}

	@Test
	void aValueThatDoesNotFitItsColumnAnswers400() {
		Host host = fx.host(program, "api.example.com");
		Request request = new Request();
		request.setHost(host);
		request.setProgram(program);
		request.setMethod(Method.GET);
		request.setSource(Source.BURP);
		request.setComputatedPath("/" + "a".repeat(600));

		DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, () -> requestRepo.saveAndFlush(request));

		ResponseEntity<String> answer = answerTo(e);
		assertEquals(400, answer.getStatusCode().value());
		assertEquals("A value is invalid or too long", answer.getBody());
	}

	@Test
	void aTextWithANulCharacterAnswers400() {
		Host duplicate = new Host();
		duplicate.setHost("nul\u0000.example.com");
		duplicate.setScope(Scope.FQDN);
		duplicate.setProgram(program);

		DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, () -> hostRepo.saveAndFlush(duplicate));

		assertEquals(400, answerTo(e).getStatusCode().value());
	}

}
