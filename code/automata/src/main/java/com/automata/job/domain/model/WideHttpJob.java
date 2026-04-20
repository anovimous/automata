package com.automata.job.domain.model;

import com.automata.job.domain.model.embedded.WideTargetConfig;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class WideHttpJob extends HttpJob {

	// Result of target selection:
	@Embedded
	private WideTargetConfig targetConfig;

}
