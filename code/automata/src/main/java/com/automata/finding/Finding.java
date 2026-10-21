package com.automata.finding;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.finding.common.enums.FindingCreator;
import com.automata.finding.common.enums.FindingStatus;
import com.automata.finding.common.enums.HumanVerdict;
import com.automata.host.Host;
import com.automata.job.domain.model.HttpJob;
import com.automata.request.Request;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Finding {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant creationDate;

	@ManyToOne(optional = false)
	private Request request;

	@ManyToOne(optional = false)
	private Host host;

	@ManyToOne
	private HttpJob job;

	@ManyToOne(optional = false)
	private Vulnerability vulnerability;

	@Column(nullable = false)
	private double severity;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private FindingStatus status;

	@Column(nullable = false)
	private double confidence;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String evidence;

	@Column(columnDefinition = "TEXT")
	private String pocReproductionSteps;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private FindingCreator createdBy;

	@Column(columnDefinition = "TEXT")
	private String aiDescription;

	@Enumerated(EnumType.STRING)
	private HumanVerdict humanVerdict;

	@Column(columnDefinition = "TEXT")
	private String humanComment;

	private Instant reviewedAt;

}
