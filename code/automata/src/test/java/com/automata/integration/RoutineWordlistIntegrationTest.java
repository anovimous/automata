package com.automata.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.repository.HttpJobRepository;
import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.routine.common.enums.Duration;
import com.automata.testsupport.KnownBug;
import com.automata.vulnerability.Vulnerability;
import com.automata.wordlist.Wordlist;
import com.automata.wordlist.WordlistRepository;

@RepositoryIntegrationTest
class RoutineWordlistIntegrationTest {

	@Autowired
	TestEntityManager em;

	@Autowired
	RoutineRepository routineRepo;

	@Autowired
	WordlistRepository wordlistRepo;

	@Autowired
	HttpJobRepository jobRepo;

	Fixtures fx;

	@BeforeEach
	void setUp() {
		fx = new Fixtures(em);
	}

	@Test
	void routineKeyIsUnique() {
		fx.routine("fuzz");

		assertThrows(org.hibernate.exception.ConstraintViolationException.class, () -> fx.routine("fuzz"));
	}

	@Test
	void routineSearchIsCaseInsensitiveAndCanBeLimitedToAVulnerability() {
		Vulnerability sqli = fx.vulnerability("SQLi", null);
		Routine fuzz = fx.routine("fuzz");
		Routine masser = fx.routine("masser");
		masser.setVulnerability(sqli);
		fx.persist(masser);
		fx.clear();

		assertEquals(List.of("fuzz"), routineRepo.findByKeyContainingIgnoreCase("FUZ", PageRequest.of(0, 10)).map(Routine::getKey).getContent());
		assertEquals(List.of("masser"), routineRepo.findByKeyContainingIgnoreCaseAndVulnerability("s", sqli, PageRequest.of(0, 10))
				.map(Routine::getKey).getContent());
		assertTrue(routineRepo.findByKeyContainingIgnoreCaseAndVulnerability("fuz", sqli, PageRequest.of(0, 10)).isEmpty());
		assertTrue(fuzz.getId() != null);
	}

	@Test
	void theTargetsAndTheConfigSchemaSurviveTheJsonColumns() throws Exception {
		Routine routine = fx.routine("fuzz");
		routine.setConfigSchema(new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"type\":\"object\"}"));
		fx.persist(routine);
		fx.clear();

		Routine loaded = routineRepo.findById(routine.getId()).orElseThrow();

		assertEquals("object", loaded.getConfigSchema().get("type").asText());
		assertEquals(4, loaded.getAllowedTargets().size());
	}

	@Test
	void aWordlistUsedByAJobIsFoundByTheUsageQuery() {
		Program program = fx.program("Acme");
		Host host = fx.host(program, "api.example.com");
		Routine routine = fx.routine("fuzz");
		Wordlist used = fx.persist(wordlist("used", "a"));
		Wordlist free = fx.persist(wordlist("free", "b"));
		NarrowHttpJob job = fx.narrowJob(routine, host, JobState.DRAFT, 1, Duration.SHORT, 1);
		job.setWordlists(new java.util.ArrayList<>(List.of(used)));
		fx.persist(job);
		fx.clear();

		assertTrue(jobRepo.existsByWordlistId(used.getId()));
		assertEquals(false, jobRepo.existsByWordlistId(free.getId()));
		assertEquals(List.of(used.getId()), jobRepo.findWordlistsByJobId(job.getId()).stream().map(Wordlist::getId).toList());
	}

	@Test
	void wordlistsOfAJobComeOrderedById() {
		Program program = fx.program("Acme");
		Host host = fx.host(program, "api.example.com");
		Routine routine = fx.routine("fuzz");
		Wordlist second = fx.persist(wordlist("b", "2"));
		Wordlist first = fx.persist(wordlist("a", "1"));
		NarrowHttpJob job = fx.narrowJob(routine, host, JobState.DRAFT, 1, Duration.SHORT, 1);
		job.setWordlists(new java.util.ArrayList<>(List.of(first, second)));
		fx.persist(job);
		fx.clear();

		List<Long> ids = jobRepo.findWordlistsByJobId(job.getId()).stream().map(Wordlist::getId).toList();

		assertEquals(ids.stream().sorted().toList(), ids);
		assertEquals(Set.of(first.getId(), second.getId()), Set.copyOf(ids));
	}

	@Test
	@KnownBug("BUG-015")
	void aSecondRoutineWithTheSameKeyIsRefusedAsAConflictNotADatabaseError() {
		fx.routine("fuzz");

		// the unique key surfaces as a DataIntegrityViolationException through the repository (HTTP 500)
		try {
			Routine duplicate = new Routine();
			duplicate.setKey("fuzz");
			duplicate.setProtocol(com.automata.routine.common.enums.Protocol.HTTP);
			duplicate.setResultContentType(com.automata.routine.common.enums.ResultContentType.TEXT);
			duplicate.setAllowedTargets(Set.of(com.automata.routine.common.enums.TargetCardinalityPair.SINGLE_HOST));
			routineRepo.saveAndFlush(duplicate);
		} catch (DataIntegrityViolationException e) {
			throw new AssertionError("raw database error: " + e.getMessage(), e);
		}
	}

	private static Wordlist wordlist(String name, String sha) {
		Wordlist wordlist = new Wordlist();
		wordlist.setName(name);
		wordlist.setSha256(sha.repeat(64).substring(0, 64));
		wordlist.setSizeBytes(10L);
		wordlist.setNumberOfLines(2);
		return wordlist;
	}
}
