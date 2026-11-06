package com.automata.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.automata.program.common.dto.ProgramDetailedResponse;
import com.automata.program.common.dto.ProgramSummaryResponse;
import com.automata.program.common.enums.Platform;
import com.automata.vulnerability.Vulnerability;

class ProgramMapperTest {

	private static Vulnerability vuln(long id) {
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setId(id);
		return vulnerability;
	}

	private static Program program() {
		Program program = new Program();
		program.setId(4L);
		program.setInsertionDate(LocalDate.of(2026, 1, 2));
		program.setName("Acme");
		program.setLink("https://acme.test");
		program.setProgramRateLimit(30);
		program.setPlatform(Platform.INTIGRITI);
		return program;
	}

	@Test
	void detailedResponseCarriesEveryFieldAndTheVulnerabilityIds() {
		Program program = program();
		program.getOutOfScopeVulns().add(vuln(1));
		program.getOutOfScopeVulns().add(vuln(2));

		ProgramDetailedResponse response = ProgramMapper.toDetailedResponse(program);

		assertEquals(4L, response.id());
		assertEquals(LocalDate.of(2026, 1, 2), response.insertionDate());
		assertEquals("Acme", response.name());
		assertEquals("https://acme.test", response.link());
		assertEquals(30, response.programRateLimit());
		assertEquals(Platform.INTIGRITI, response.platform());
		assertEquals(Set.of(1L, 2L), response.outOfScopeVulnIds());
	}

	@Test
	void detailedResponseOfAProgramWithoutOptionalFieldsHasNullsAndNoVulnerabilities() {
		Program program = program();
		program.setLink(null);
		program.setPlatform(null);

		ProgramDetailedResponse response = ProgramMapper.toDetailedResponse(program);

		assertNull(response.link());
		assertNull(response.platform());
		assertTrue(response.outOfScopeVulnIds().isEmpty());
	}

	@Test
	void summaryResponseCarriesOnlyTheSummaryFields() {
		ProgramSummaryResponse response = ProgramMapper.toSummaryResponse(program());

		assertEquals(4L, response.id());
		assertEquals("Acme", response.name());
		assertEquals("https://acme.test", response.link());
		assertEquals(Platform.INTIGRITI, response.platform());
		assertEquals(LocalDate.of(2026, 1, 2), response.insertionDate());
	}

	@Test
	void summaryPageKeepsThePagingMetadata() {
		Page<Program> page = new PageImpl<>(List.of(program()), PageRequest.of(1, 1), 3);

		Page<ProgramSummaryResponse> mapped = ProgramMapper.toSummaryResponse(page);

		assertEquals(3, mapped.getTotalElements());
		assertEquals(1, mapped.getNumber());
		assertEquals(1, mapped.getContent().size());
	}
}
