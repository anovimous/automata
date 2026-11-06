package com.automata.finding;

import com.automata.finding.common.dto.FindingDetailedResponse;
import com.automata.finding.common.dto.FindingSummaryResponse;

public abstract class FindingMapper {

	public static FindingDetailedResponse toDetailedResponse(Finding finding) {
		return FindingDetailedResponse.builder().id(finding.getId()).creationDate(finding.getCreationDate())
				.requestId(finding.getRequest().getId()).hostId(finding.getHost().getId())
				.jobId(finding.getJob() != null ? finding.getJob().getId() : null)
				.vulnerabilityId(finding.getVulnerability().getId()).severity(finding.getSeverity())
				.status(finding.getStatus()).confidence(finding.getConfidence()).evidence(finding.getEvidence())
				.pocReproductionSteps(finding.getPocReproductionSteps()).createdBy(finding.getCreatedBy())
				.aiDescription(finding.getAiDescription()).humanVerdict(finding.getHumanVerdict())
				.humanComment(finding.getHumanComment()).reviewedAt(finding.getReviewedAt()).build();
	}

	public static FindingSummaryResponse toSummaryResponse(Finding finding) {
		return FindingSummaryResponse.builder().id(finding.getId()).requestId(finding.getRequest().getId())
				.hostId(finding.getHost().getId()).vulnerabilityId(finding.getVulnerability().getId())
				.severity(finding.getSeverity()).status(finding.getStatus()).confidence(finding.getConfidence())
				.createdBy(finding.getCreatedBy()).humanVerdict(finding.getHumanVerdict())
				.creationDate(finding.getCreationDate()).build();
	}

}
