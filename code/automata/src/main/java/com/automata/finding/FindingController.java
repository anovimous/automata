package com.automata.finding;

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
import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingDetailedResponse;
import com.automata.finding.common.dto.FindingFilter;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.dto.FindingReviewRequest;
import com.automata.finding.common.dto.FindingSummaryResponse;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/findings")
@RequiredArgsConstructor
public class FindingController {

	private final FindingService findingService;

	@PostMapping("")
	public ResponseEntity<Void> createFinding(@RequestBody FindingCreationRequest request) {

		Finding finding = findingService.createFinding(request);

		return ResponseEntity.created(URI.create(String.format("/api/findings/%d", finding.getId()))).build();

	}

	@GetMapping("/{findingId}")
	public ResponseEntity<FindingDetailedResponse> getFinding(@PathVariable Long findingId) {

		Finding finding = findingService.getFinding(findingId);

		return ResponseEntity.ok(FindingMapper.toDetailedResponse(finding));

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<FindingSummaryResponse>> getFindings(
			@RequestParam(required = false) Long hostId, @RequestParam(required = false) Long requestId,
			@RequestParam(required = false) Long jobId, @RequestParam(required = false) FindingStatus status,
			@RequestParam(required = false) HumanVerdict humanVerdict,
			@RequestParam(required = false) FindingCreator createdBy,
			@PageableDefault(size = 20, sort = "creationDate", direction = Sort.Direction.DESC) Pageable pageable) {

		FindingFilter filter = new FindingFilter(hostId, requestId, jobId, status, humanVerdict, createdBy);

		Page<Finding> findings = findingService.getFindings(filter, pageable);

		Page<FindingSummaryResponse> responses = findings.map(FindingMapper::toSummaryResponse);

		return ResponseEntity.ok(new PageHolderResponse<>(responses));

	}

	@PatchMapping("/{findingId}")
	public ResponseEntity<Void> patchFinding(@PathVariable Long findingId,
			@RequestBody FindingPatchRequest request) {

		findingService.patchFinding(findingId, request);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{findingId}/review")
	public ResponseEntity<Void> reviewFinding(@PathVariable Long findingId,
			@RequestBody FindingReviewRequest request) {

		findingService.reviewFinding(findingId, request);

		return ResponseEntity.status(204).build();

	}

	@DeleteMapping("/{findingId}")
	public ResponseEntity<Void> deleteFinding(@PathVariable Long findingId) {

		findingService.deleteFinding(findingId);

		return ResponseEntity.status(204).build();

	}

}
