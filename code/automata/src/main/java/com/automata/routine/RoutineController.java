package com.automata.routine;

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
import com.automata.routine.common.dto.RoutineCreationRequest;
import com.automata.routine.common.dto.RoutineDetailedResponseDto;
import com.automata.routine.common.dto.RoutinePatchRequest;
import com.automata.routine.common.dto.RoutineSummaryResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/routines")
@RequiredArgsConstructor
public class RoutineController {

	private final RoutineService routineService;

	private final ObjectMapper mapper;

	@GetMapping("/{routineId}")
	public ResponseEntity<RoutineDetailedResponseDto> getRoutine(@PathVariable Long routineId) {

		Routine routine = routineService.getRoutineById(routineId);

		return ResponseEntity.ok(RoutineMapper.toDetailedResponse(routine));

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<RoutineSummaryResponseDto>> getRoutines(
			@RequestParam(defaultValue = "") String query, @RequestParam(required = false) Long vulnId,
			@PageableDefault(size = 30, sort = { "key" }, direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Routine> routines;

		if (vulnId == null)
			routines = routineService.getRoutinesPagedAndFilteredOnQueryString(query, pageable);
		else
			routines = routineService.getVulnerabilityRoutinesPagedAndFilteredOnQueryString(vulnId, query, pageable);

		Page<RoutineSummaryResponseDto> routineResponses = RoutineMapper.toSummaryResponse(routines);

		return ResponseEntity.ok(new PageHolderResponse<>(routineResponses));

	}

	@PostMapping("")
	public ResponseEntity<Void> createRoutine(@RequestBody RoutineCreationRequest request) {

		Routine toBeCreatedRoutine = mapper.convertValue(request, Routine.class);

		Long vulnId = request.vulnerabilityId();

		Routine createdRoutine = routineService.createRoutine(toBeCreatedRoutine, vulnId);

		return ResponseEntity.status(201)
				.location(URI.create(String.format("/api/routines/%d", createdRoutine.getId()))).build();

	}

	@PatchMapping("/{routineId}")
	public ResponseEntity<Void> patchRoutine(@PathVariable Long routineId,
			@RequestBody RoutinePatchRequest patchRequest) {

		routineService.patchRoutine(routineId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{routineId}")
	public ResponseEntity<Void> deleteRoutine(@PathVariable Long routineId) {

		routineService.deleteRoutine(routineId);

		return ResponseEntity.status(204).build();

	}

}
