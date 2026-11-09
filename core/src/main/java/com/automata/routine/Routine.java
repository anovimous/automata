package com.automata.routine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.automata.routine.common.enums.ResultContentType;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.automata.vulnerability.Vulnerability;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Routine {

	public static final int MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH = 5000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false, updatable = false, unique = true)
	private String key;

	@Column(nullable = false)
	private String description;

	// How an AI agent should use the routine: returned only by the routine's own route (GET /api/routines/{id}),
	// never in the list.
	@Column(nullable = false, length = MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH)
	private String usageDescriptionForAgent;

	@UpdateTimestamp
	private Instant updatedAt;

	private boolean isAvailableAtConsumer;

	// Tells whether the routine is meant to run with at least one wordlist. It is information for the callers
	// (the AI reads it), the core does not enforce it when a job is created.
	private boolean wordlistRequired;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", nullable = false)
	private Set<TargetCardinalityPair> allowedTargets;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode configSchema;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ResultContentType resultContentType;

	@ManyToOne
	private Vulnerability vulnerability;

}
