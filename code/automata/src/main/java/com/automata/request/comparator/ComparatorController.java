package com.automata.request.comparator;

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
import com.automata.request.comparator.common.dto.ComparatorCreationRequest;
import com.automata.request.comparator.common.dto.ComparatorPatchRequest;
import com.automata.request.comparator.common.dto.ComparatorResponse;
import com.automata.request.comparator.common.enums.Schema;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/comparators")
@RequiredArgsConstructor
public class ComparatorController {

	private final ComparatorService comparatorService;

	@GetMapping("/{comparatorId}")
	public ResponseEntity<ComparatorResponse> getComparator(@PathVariable Long comparatorId) {

		Comparator comparator = comparatorService.getComparatorById(comparatorId);

		return ResponseEntity.ok(ComparatorMapper.toResponse(comparator));

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<ComparatorResponse>> getComparators(
			@RequestParam(defaultValue = "") String query, @RequestParam(required = false) Schema schema,
			@PageableDefault(size = 30, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Comparator> comparators = comparatorService.getComparatorsPagedAndFiltered(query, schema, pageable);

		Page<ComparatorResponse> comparatorsResponse = comparators.map(ComparatorMapper::toResponse);

		return ResponseEntity.ok(new PageHolderResponse<>(comparatorsResponse));

	}

	@PostMapping("")
	public ResponseEntity<Void> createComparator(@RequestBody ComparatorCreationRequest req) {

		Comparator toCreateComparator = ComparatorMapper.toComparator(req);

		Comparator createdComparator = comparatorService.createComparator(toCreateComparator, req.modifierIds());

		return ResponseEntity.status(201).location(URI.create("/api/comparators/" + createdComparator.getId())).build();

	}

	@PatchMapping("/{comparatorId}")
	public ResponseEntity<Void> patchComparator(@PathVariable Long comparatorId,
			@RequestBody ComparatorPatchRequest patchRequest) {

		comparatorService.patchComparator(comparatorId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{comparatorId}")
	public ResponseEntity<Void> deleteComparator(@PathVariable Long comparatorId) {

		comparatorService.deleteComparator(comparatorId);

		return ResponseEntity.status(204).build();

	}

}
