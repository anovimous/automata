package com.automata.routine;

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
import com.automata.routine.common.dto.RoutineCreationRequest;
import com.automata.routine.common.dto.RoutinePatchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineController {

	private final RoutineService routineService;

	@GetMapping("/{routineId}")
	public ResponseEntity<Routine> getRoutine(@PathVariable Long routineId) {

		Routine routine = routineService.getRoutineById(routineId);

		return ResponseEntity.ok(routine);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<Routine>> getRoutines(@RequestParam(defaultValue = "") String query,
			@RequestParam(required = false) Long vulnId,
			@PageableDefault(size = 30, sort = { "key" }, direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Routine> routines;

		if (vulnId == null)
			routines = routineService.getRoutinesPagedAndFilteredOnQueryString(query, pageable);
		else
			routines = routineService.getVulnerabilityRoutinesPagedAndFilteredOnQueryString(vulnId, query, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(routines));

	}

	@PostMapping("")
	public ResponseEntity<Routine> createRoutine(@RequestBody RoutineCreationRequest request) {

		ObjectMapper mapper = new ObjectMapper();

		Routine toBeCreatedRoutine = mapper.convertValue(request, Routine.class);

		Long vulnId = request.vulnerabilityId();

		Routine createdRoutine = routineService.createRoutine(toBeCreatedRoutine, vulnId);

		return ResponseEntity.status(201).body(createdRoutine);

	}

	@PatchMapping("/{routineId}")
	public ResponseEntity<Routine> patchRoutine(@PathVariable Long routineId,
			@RequestBody RoutinePatchRequest patchRequest) {

		Routine routine = routineService.patchRoutine(routineId, patchRequest);

		return ResponseEntity.ok(routine);
	}

	@DeleteMapping("/{routineId}")
	public ResponseEntity<Void> deleteRoutine(@PathVariable Long routineId) {

		routineService.deleteRoutine(routineId);

		return ResponseEntity.status(204).build();

	}

}
