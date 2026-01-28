package com.automata.request.comparator;

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
import com.automata.request.comparator.common.dto.ComparatorPatchRequest;
import com.automata.request.comparator.common.enums.Schema;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/comparators")
@RequiredArgsConstructor
public class ComparatorController {

	private final ComparatorService comparatorService;

	@GetMapping("/{comparatorId}")
	public ResponseEntity<Comparator> getComparator(@PathVariable Long comparatorId) {

		Comparator comparator = comparatorService.getComparatorById(comparatorId);

		return ResponseEntity.ok(comparator);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<Comparator>> getComparators(@RequestParam(defaultValue = "") String query,
			@RequestParam(required = false) Long programId, @RequestParam(required = false) Long hostId,
			@RequestParam(required = false) Schema schema,
			@PageableDefault(size = 30, sort = "creationDate", direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Comparator> comparators = comparatorService.getComparatorsPagedAndFiltered(query, programId, hostId,
				schema, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(comparators));

	}

	@PostMapping("")
	public ResponseEntity<Comparator> createComparator(@RequestBody Comparator comparator) {

		Comparator createdComparator = comparatorService.createComparator(comparator);

		return ResponseEntity.status(201).body(createdComparator);

	}

	@PatchMapping("/{comparatorId}")
	public ResponseEntity<Comparator> patchComparator(@PathVariable Long comparatorId,
			@RequestBody ComparatorPatchRequest patchRequest) {

		Comparator comparator = comparatorService.patchComparator(comparatorId, patchRequest);

		return ResponseEntity.ok(comparator);
	}

	@DeleteMapping("/{comparatorId}")
	public ResponseEntity<Void> deleteComparator(@PathVariable Long comparatorId) {

		comparatorService.deleteComparator(comparatorId);

		return ResponseEntity.status(204).build();

	}

}
