package com.automata.request.equalityset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.request.equalityset.common.dto.PatchRequestEqualitySetRequest;
import com.automata.request.equalityset.common.dto.RequestEqualitySetResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests/equality-sets")
@RequiredArgsConstructor
public class RequestEqualitySetController {

	private final RequestEqualityService equalityService;

	@GetMapping("/{setId}")
	public ResponseEntity<RequestEqualitySetResponse> getEqualitySet(@PathVariable Long setId) {

		RequestEqualitySet set = equalityService.getEqualitySet(setId);

		RequestEqualitySetResponse response = RequestEqualitySetMapper.toResponse(set);

		return ResponseEntity.ok(response);

	}

	@PatchMapping("/{setId}")
	public ResponseEntity<Void> patchEqualitySet(@PathVariable Long setId,
			@RequestBody PatchRequestEqualitySetRequest request) {

		equalityService.patchEqualitySet(setId, request.alias(), request.autoApproval());

		return ResponseEntity.status(204).build();

	}

}
