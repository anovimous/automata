package com.automata.program;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/programs")
@RequiredArgsConstructor
public class ProgramController {

	private final ProgramService programService;

	@GetMapping("/{programId}")
	public ResponseEntity<Program> getProgram(@PathVariable Long programId) {

		Program program = programService.getProgramById(programId);

		return ResponseEntity.ok(program);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<Program>> getPrograms(@RequestParam(defaultValue = "") String query,
			@PageableDefault(size = 10, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Program> programs = programService.getProgramsPagedAndFilteredOnQueryString(query, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(programs));

	}

	@PostMapping("")
	public ResponseEntity<Program> createProgram(@RequestBody Program program) {

		Program createdProgram = programService.createProgram(program);

		return ResponseEntity.status(201).body(createdProgram);

	}

	@PatchMapping("/{programId}")
	public ResponseEntity<Program> patchProgram(@PathVariable Long programId,
			@RequestBody PatchProgramRequest patchRequest) {

		Program program = programService.patchProgram(programId, patchRequest);

		return ResponseEntity.ok(program);
	}

	@DeleteMapping("/{programId}")
	public ResponseEntity<Void> deleteProgram(@PathVariable Long programId) {

		return ResponseEntity.status(503).build();

//		waiting for service implementation

//		programService.deleteProgram(programId);
//		
//		return ResponseEntity.status(204).build();

	}

}
