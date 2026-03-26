package com.automata.job;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

import com.automata.job.common.enums.HttpJobScope;
import com.automata.job.common.enums.JobState;
import com.automata.job.common.enums.ResultsVerbosity;
import com.automata.job.selector.TargetSelector;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Builder
@Getter
@Setter
public class HttpJobGenericDetails {

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private HttpJobScope httpJobScope;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private JobState currentState;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private JobState requestedState;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ResultsVerbosity verbosity;

	// 1->3:Lazy prioritized,4:Eager (5: reserved for retried eager runs)
	@Column(nullable = false)
	private Integer priority;

	@Column(nullable = false)
	private Integer rate;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private TargetSelector targetSelector;

	// contains matchandreplace config
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private GenericConfig genericConfig;

	// config specific to this routine, only parsed and consumed by the executor
	@Nullable
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode customConfig;

}
