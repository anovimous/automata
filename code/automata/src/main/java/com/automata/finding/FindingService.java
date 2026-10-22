package com.automata.finding;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.finding.common.dto.FindingCreationRequest;
import com.automata.finding.common.dto.FindingFilter;
import com.automata.finding.common.dto.FindingPatchRequest;
import com.automata.finding.common.dto.FindingReviewRequest;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.host.Host;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.repository.HttpJobRepository;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FindingService {

	private final FindingRepository findingRepo;

	private final RequestRepository requestRepo;

	private final HttpJobRepository jobRepo;

	private final VulnerabilityRepository vulnerabilityRepo;

	@Transactional(readOnly = true)
	public Finding getFinding(Long findingId) {
		return findingRepo.findById(findingId)
				.orElseThrow(() -> new EntityNotFoundException("Finding not found"));
	}

	@Transactional(readOnly = true)
	public Page<Finding> getFindings(FindingFilter filter, Pageable pageable) {
		Specification<Finding> spec = FindingUtils.buildSpecification(filter);
		return findingRepo.findAll(spec, pageable);
	}

	@Transactional
	public Finding createFinding(FindingCreationRequest request) {

		validateSeverity(request.severity());
		validateConfidence(request.confidence());
		validatePocForStatus(request.status(), request.pocReproductionSteps());

		Request req = requestRepo.findById(request.requestId())
				.orElseThrow(() -> new EntityNotFoundException("Request not found"));

		Host host = req.getHost();

		HttpJob job = null;
		if (request.jobId() != null)
			job = jobRepo.findById(request.jobId())
					.orElseThrow(() -> new EntityNotFoundException("HttpJob not found"));

		Vulnerability vulnerability = vulnerabilityRepo.findById(request.vulnerabilityId())
				.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

		Finding finding = Finding.builder().request(req).host(host).job(job).vulnerability(vulnerability)
				.severity(request.severity()).status(request.status()).confidence(request.confidence())
				.evidence(request.evidence()).pocReproductionSteps(request.pocReproductionSteps())
				.createdBy(request.createdBy()).aiDescription(request.aiDescription()).build();

		return findingRepo.save(finding);

	}

	@Transactional
	public void patchFinding(Long findingId, FindingPatchRequest patch) {

		Finding finding = findingRepo.findById(findingId)
				.orElseThrow(() -> new EntityNotFoundException("Finding not found"));

		patch.severity().ifPresent(s -> {
			validateSeverity(s);
			finding.setSeverity(s);
		});

		patch.confidence().ifPresent(c -> {
			validateConfidence(c);
			finding.setConfidence(c);
		});

		patch.status().ifPresent(finding::setStatus);
		patch.evidence().ifPresent(finding::setEvidence);

		if (patch.setPocReproductionStepsNull())
			finding.setPocReproductionSteps(null);
		else
			patch.pocReproductionSteps().ifPresent(finding::setPocReproductionSteps);

		if (patch.setAiDescriptionNull())
			finding.setAiDescription(null);
		else
			patch.aiDescription().ifPresent(finding::setAiDescription);

		validatePocForStatus(finding.getStatus(), finding.getPocReproductionSteps());

		findingRepo.save(finding);

	}

	@Transactional
	public void reviewFinding(Long findingId, FindingReviewRequest request) {

		Finding finding = findingRepo.findById(findingId)
				.orElseThrow(() -> new EntityNotFoundException("Finding not found"));

		finding.setHumanVerdict(request.verdict());
		finding.setHumanComment(request.comment());
		finding.setReviewedAt(Instant.now());

		findingRepo.save(finding);

	}

	@Transactional
	public void deleteFinding(Long findingId) {

		Finding finding = findingRepo.findById(findingId)
				.orElseThrow(() -> new EntityNotFoundException("Finding not found"));

		findingRepo.delete(finding);

	}

	private void validateSeverity(double severity) {
		if (severity < 0.0 || severity > 10.0)
			throw new IllegalArgumentException("Severity must be between 0.0 and 10.0");
	}

	private void validateConfidence(double confidence) {
		if (confidence < 0.0 || confidence > 10.0)
			throw new IllegalArgumentException("Confidence must be between 0.0 and 10.0");
	}

	private void validatePocForStatus(FindingStatus status, String pocReproductionSteps) {
		if (status == FindingStatus.CONFIRMED && (pocReproductionSteps == null || pocReproductionSteps.isBlank()))
			throw new IllegalArgumentException("pocReproductionSteps is required when status is CONFIRMED");
	}

}
