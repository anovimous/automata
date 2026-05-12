package com.automata.program;

import java.util.stream.Collectors;

import org.springframework.data.domain.Page;

import com.automata.program.common.dto.ProgramDetailedResponse;
import com.automata.program.common.dto.ProgramSummaryResponse;
import com.automata.vulnerability.Vulnerability;

public abstract class ProgramMapper {

	public static ProgramDetailedResponse toDetailedResponse(Program program) {

		return ProgramDetailedResponse.builder().id(program.getId()).insertionDate(program.getInsertionDate())
				.name(program.getName()).link(program.getLink()).programRateLimit(program.getProgramRateLimit())
				.platform(program.getPlatform())
				.outOfScopeVulnIds(
						program.getOutOfScopeVulns().stream().map(Vulnerability::getId).collect(Collectors.toSet()))
				.build();
	}

	public static Page<ProgramSummaryResponse> toSummaryResponse(Page<Program> programs) {
		
		return programs.map(ProgramMapper::toSummaryResponse);
	}

	public static ProgramSummaryResponse toSummaryResponse(Program program) {

		return ProgramSummaryResponse.builder().id(program.getId()).insertionDate(program.getInsertionDate())
				.name(program.getName()).link(program.getLink()).platform(program.getPlatform()).build();
	}

}
