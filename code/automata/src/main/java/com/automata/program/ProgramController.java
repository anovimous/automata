package com.automata.program;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.program.common.dto.PatchProgramRequest;
import com.automata.program.common.dto.ProgramDetailedResponse;
import com.automata.program.common.dto.ProgramSummaryResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramController {

	private final ProgramService programService;

	@GetMapping("/{programId}")
	public ResponseEntity<ProgramDetailedResponse> getProgram(@PathVariable Long programId) {

		Program program = programService.getProgramWithVulns(programId);

		ProgramDetailedResponse response = ProgramMapper.toDetailedResponse(program);

		return ResponseEntity.ok(response);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<ProgramSummaryResponse>> getPrograms(
			@RequestParam(defaultValue = "") String query, @PageableDefault(size = 10, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Program> programs = programService.getProgramsPagedAndFilteredOnQueryString(query, pageable);

		Page<ProgramSummaryResponse> programResponses = ProgramMapper.toSummaryResponse(programs);

		return ResponseEntity.ok(new PageHolderResponse<>(programResponses));

	}

	@PostMapping("")
	public ResponseEntity<Void> createProgram(@RequestBody Program program) {

		Program createdProgram = programService.createProgram(program);

		return ResponseEntity.status(201)
				.location(URI.create(String.format("/api/programs/%d", createdProgram.getId()))).build();

	}

	@PatchMapping("/{programId}")
	public ResponseEntity<Void> patchProgram(@PathVariable Long programId,
			@RequestBody PatchProgramRequest patchRequest) {

		programService.patchProgram(programId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{programId}")
	public ResponseEntity<Void> deleteProgram(@PathVariable Long programId) {

		programService.deleteProgram(programId);

		return ResponseEntity.status(204).build();

	}

}
