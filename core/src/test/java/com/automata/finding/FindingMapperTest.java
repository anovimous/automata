package com.automata.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.automata.finding.common.dto.FindingDetailedResponse;
import com.automata.finding.common.dto.FindingSummaryResponse;
import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.host.Host;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.request.Request;
import com.automata.vulnerability.Vulnerability;

class FindingMapperTest {

	private Host host(Long id) {
		Host h = new Host();
		h.setId(id);
		return h;
	}

	@Test
	void toDetailedResponseMapsAllFields() {
		Instant now = Instant.now();
		Request req = Request.of(10L);
		Host h = host(1L);
		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(3L);
		Vulnerability vuln = new Vulnerability();
		vuln.setId(5L);

		Finding finding = Finding.builder().id(1L).creationDate(now).request(req).host(h).job(job)
				.vulnerability(vuln).severity(7.5).status(FindingStatus.CONFIRMED).confidence(8.0)
				.evidence("XSS found").pocReproductionSteps("Step 1").createdBy(FindingCreator.AI)
				.aiDescription("desc").humanVerdict(HumanVerdict.TRUE_POSITIVE).humanComment("confirmed")
				.reviewedAt(now).build();

		FindingDetailedResponse response = FindingMapper.toDetailedResponse(finding);

		assertEquals(1L, response.id());
		assertEquals(now, response.creationDate());
		assertEquals(10L, response.requestId());
		assertEquals(1L, response.hostId());
		assertEquals(3L, response.jobId());
		assertEquals(5L, response.vulnerabilityId());
		assertEquals(7.5, response.severity());
		assertEquals(FindingStatus.CONFIRMED, response.status());
		assertEquals(8.0, response.confidence());
		assertEquals("XSS found", response.evidence());
		assertEquals("Step 1", response.pocReproductionSteps());
		assertEquals(FindingCreator.AI, response.createdBy());
		assertEquals("desc", response.aiDescription());
		assertEquals(HumanVerdict.TRUE_POSITIVE, response.humanVerdict());
		assertEquals("confirmed", response.humanComment());
		assertEquals(now, response.reviewedAt());
	}

	@Test
	void toDetailedResponseNullableFieldsHandled() {
		Request req = Request.of(10L);
		Host h = host(1L);
		Vulnerability vuln = new Vulnerability();
		vuln.setId(5L);

		Finding finding = Finding.builder().id(2L).request(req).host(h).vulnerability(vuln).severity(5.0)
				.status(FindingStatus.SUGGESTED).confidence(6.0).evidence("evidence")
				.createdBy(FindingCreator.USER).build();

		FindingDetailedResponse response = FindingMapper.toDetailedResponse(finding);

		assertNull(response.jobId());
		assertNull(response.pocReproductionSteps());
		assertNull(response.aiDescription());
		assertNull(response.humanVerdict());
		assertNull(response.humanComment());
		assertNull(response.reviewedAt());
	}

	@Test
	void toSummaryResponseMapsFields() {
		Instant now = Instant.now();
		Request req = Request.of(10L);
		Host h = host(1L);
		Vulnerability vuln = new Vulnerability();
		vuln.setId(5L);

		Finding finding = Finding.builder().id(1L).request(req).host(h).vulnerability(vuln).severity(7.5)
				.status(FindingStatus.CONFIRMED).confidence(8.0).createdBy(FindingCreator.AI)
				.humanVerdict(HumanVerdict.FALSE_POSITIVE).creationDate(now).build();

		FindingSummaryResponse response = FindingMapper.toSummaryResponse(finding);

		assertEquals(1L, response.id());
		assertEquals(10L, response.requestId());
		assertEquals(1L, response.hostId());
		assertEquals(5L, response.vulnerabilityId());
		assertEquals(7.5, response.severity());
		assertEquals(FindingStatus.CONFIRMED, response.status());
		assertEquals(8.0, response.confidence());
		assertEquals(FindingCreator.AI, response.createdBy());
		assertEquals(HumanVerdict.FALSE_POSITIVE, response.humanVerdict());
		assertEquals(now, response.creationDate());
	}

}
