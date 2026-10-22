package com.automata.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.host.common.dto.HostDetailedResponse;
import com.automata.host.common.enums.Scope;
import com.automata.program.Program;

class HostMapperTest {

	private Host hostWithAiFields() {
		Host host = new Host();
		host.setId(1L);
		host.setHost("api.example.com");
		host.setLevel(1);
		host.setScope(Scope.FQDN);

		Program program = new Program();
		program.setId(10L);
		host.setProgram(program);

		host.setDescription("Main API backend");
		host.setTechnologies(List.of("nginx", "spring-boot"));
		host.setNotes("Rate-limited to 100 req/s");
		return host;
	}

	@Test
	void detailedResponseIncludesDescription() {
		Host host = hostWithAiFields();

		HostDetailedResponse response = HostMapper.toDetailedResponse(host);

		assertEquals("Main API backend", response.description());
	}

	@Test
	void detailedResponseIncludesTechnologies() {
		Host host = hostWithAiFields();

		HostDetailedResponse response = HostMapper.toDetailedResponse(host);

		assertEquals(List.of("nginx", "spring-boot"), response.technologies());
	}

	@Test
	void detailedResponseIncludesNotes() {
		Host host = hostWithAiFields();

		HostDetailedResponse response = HostMapper.toDetailedResponse(host);

		assertEquals("Rate-limited to 100 req/s", response.notes());
	}

	@Test
	void detailedResponseHandlesNullAiFields() {
		Host host = new Host();
		host.setId(2L);
		host.setHost("plain.example.com");
		host.setLevel(0);
		host.setScope(Scope.WILDCARD);

		Program program = new Program();
		program.setId(20L);
		host.setProgram(program);

		HostDetailedResponse response = HostMapper.toDetailedResponse(host);

		assertNull(response.description());
		assertNull(response.technologies());
		assertNull(response.notes());
	}

}
