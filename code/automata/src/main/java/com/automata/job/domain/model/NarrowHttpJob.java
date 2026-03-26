package com.automata.job.domain.model;

import com.automata.host.Host;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.program.Program;
import com.automata.routine.common.enums.Duration;
import com.automata.tenant.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class NarrowHttpJob extends HttpJob {

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Duration duration;

	// Result of target selection:
	@Embedded
	private NarrowTargetConfig targetConfig;

	@ManyToOne(optional = false)
	private Program program;

	@ManyToOne(optional = false)
	private Host host;

	// Tenant is mainly for authentication purposes for now
	@ManyToOne(optional = false)
	private Tenant tenant;

}
