package com.automata.agentcontext;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.automata.agentcontext.common.dto.AgentFindingDetailedResponse;
import com.automata.agentcontext.common.dto.AgentFindingSummaryResponse;
import com.automata.agentcontext.common.dto.AiFindingCreationRequest;
import com.automata.common.dto.response.PageHolderResponse;
import com.automata.finding.Finding;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;

import lombok.RequiredArgsConstructor;

// The finding operations of the AI agent. They only reach the findings of the requests the context
// gives access to: any other finding answers 404, like a finding that does not exist.
@RestController
@RequestMapping("/api/agent-contexts/uuid/{uuid}/findings")
@RequiredArgsConstructor
public class AgentContextFindingController {

	private static final int MAX_PAGE_SIZE = 100;

	private final AgentContextFindingService findingService;

	@PostMapping("")
	public ResponseEntity<Void> createFinding(@PathVariable UUID uuid, @RequestBody AiFindingCreationRequest request) {

		Finding finding = findingService.createFinding(uuid, request);

		return ResponseEntity.created(URI.create(String.format("/api/agent-contexts/uuid/%s/findings/%d", uuid,
				finding.getId()))).build();

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<AgentFindingSummaryResponse>> getFindings(@PathVariable UUID uuid,
			@RequestParam(required = false) Long requestId, @RequestParam(required = false) Long jobId,
			@RequestParam(required = false) FindingStatus status,
			@RequestParam(required = false) HumanVerdict humanVerdict,
			@RequestParam(required = false) FindingCreator createdBy,
			@PageableDefault(size = 20, sort = "creationDate", direction = Sort.Direction.DESC) Pageable pageable) {

		Pageable capped = pageable.getPageSize() > MAX_PAGE_SIZE
				? PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
				: pageable;

		return ResponseEntity.ok(new PageHolderResponse<>(findingService
				.getFindings(uuid, requestId, jobId, status, humanVerdict, createdBy, capped)
				.map(finding -> AgentContextMapper.toAgentFindingSummary(finding, uuid))));

	}

	@GetMapping("/{findingId}")
	public ResponseEntity<AgentFindingDetailedResponse> getFinding(@PathVariable UUID uuid,
			@PathVariable Long findingId) {

		return ResponseEntity.ok(AgentContextMapper.toAgentFindingDetail(findingService.getFinding(uuid, findingId),
				uuid));

	}

	@PatchMapping("/{findingId}")
	public ResponseEntity<Void> patchFinding(@PathVariable UUID uuid, @PathVariable Long findingId,
			@RequestBody FindingPatchRequest request) {

		findingService.patchFinding(uuid, findingId, request);

		return ResponseEntity.status(204).build();

	}

}
