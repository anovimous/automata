package com.automata.agentcontext;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.agentcontext.common.dto.AiFindingCreationRequest;
import com.automata.finding.Finding;
import com.automata.finding.FindingService;
import com.automata.finding.FindingUtils;
import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingFilter;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.finding.common.exception.InvalidFindingStateException;
import com.automata.job.service.HttpJobQueryService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

// The finding operations of the AI agent. A finding is visible when its request is readable in the
// context (see AgentContextService.isRequestInContext), any other finding answers as if it did not
// exist. Only the findings created by the agent of the context, and not yet reviewed, can be edited.
@Service
@RequiredArgsConstructor
public class AgentContextFindingService {

	private final AgentContextService contextService;

	private final FindingService findingService;

	private final HttpJobQueryService jobQueryService;

	@Transactional
	public Finding createFinding(UUID contextUuid, AiFindingCreationRequest request) {

		this.requireFields(request);

		AgentContext context = contextService.getContextByUuid(contextUuid);

		if (!contextService.isRequestInContext(context, request.requestId()))
			throw new EntityNotFoundException("Request not found");

		// Only an AI job of this context that targeted the request can back the finding
		if (request.jobId() != null) {
			jobQueryService.getJobOfContext(contextUuid, request.jobId());
			jobQueryService.getJobOfRequest(request.requestId(), request.jobId());
		}

		FindingCreationRequest creation = new FindingCreationRequest(request.requestId(), request.jobId(),
				request.vulnerabilityId(), request.severity(), request.status(), request.confidence(),
				request.evidence(), request.pocReproductionSteps(), FindingCreator.AI, request.aiDescription());

		return findingService.createFinding(creation, context);

	}

	@Transactional(readOnly = true)
	public Page<Finding> getFindings(UUID contextUuid, Long requestId, Long jobId, FindingStatus status,
			HumanVerdict humanVerdict, FindingCreator createdBy, Pageable pageable) {

		AgentContext context = contextService.getContextByUuid(contextUuid);

		Specification<Finding> spec = FindingUtils
				.buildSpecification(new FindingFilter(null, requestId, jobId, status, humanVerdict, createdBy))
				.and(FindingUtils.inRequestsOrEqualitySets(contextService.getRequestItemIds(context),
						contextService.getEqualitySetItemIds(context)));

		return findingService.getFindings(spec, pageable);

	}

	@Transactional(readOnly = true)
	public Finding getFinding(UUID contextUuid, Long findingId) {

		Finding finding = findingService.getFinding(findingId);

		AgentContext context = contextService.getContextByUuid(contextUuid);

		if (!contextService.isRequestInContext(context, finding.getRequest().getId()))
			throw new EntityNotFoundException("Finding not found");

		return finding;

	}

	@Transactional
	public void patchFinding(UUID contextUuid, Long findingId, FindingPatchRequest patch) {

		Finding finding = this.getFinding(contextUuid, findingId);

		boolean ownFinding = finding.getCreatedBy() == FindingCreator.AI && finding.getAgentContext() != null
				&& finding.getAgentContext().getUuid().equals(contextUuid);

		if (!ownFinding)
			throw new InvalidFindingStateException("Only a finding created by the agent of this context can be edited");

		if (finding.getHumanVerdict() != null)
			throw new InvalidFindingStateException("A finding reviewed by a human cannot be edited");

		findingService.patchFinding(finding, patch);

	}

	void requireFields(AiFindingCreationRequest request) {

		if (request.requestId() == null)
			throw new IllegalArgumentException("requestId is required");
		if (request.vulnerabilityId() == null)
			throw new IllegalArgumentException("vulnerabilityId is required");
		if (request.severity() == null)
			throw new IllegalArgumentException("severity is required");
		if (request.confidence() == null)
			throw new IllegalArgumentException("confidence is required");
		if (request.status() == null)
			throw new IllegalArgumentException("status is required");
		if (request.evidence() == null || request.evidence().isBlank())
			throw new IllegalArgumentException("evidence is required");

	}

}
