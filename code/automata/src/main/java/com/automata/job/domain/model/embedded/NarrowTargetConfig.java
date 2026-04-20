package com.automata.job.domain.model.embedded;

import java.util.ArrayList;
import java.util.List;

import com.automata.host.Host;
import com.automata.job.domain.model.NarrowJobTargetRequest;

import jakarta.persistence.Embeddable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
public class NarrowTargetConfig {

	@ManyToOne
	private Host targetHost;

	@Builder.Default
	@OneToMany(mappedBy = "job")
	private List<NarrowJobTargetRequest> targetRequests = new ArrayList<>();

	// - Equality sets will not be stored since they are not a direct target, but an
	// indirect one
	// - In all cases, equality sets which are targeted can be queried via querying
	// the equality sets of the affected requests since it doesn't differ whether
	// the equality set or the request is targeted (request <=> equalitySet)
	// - Equality sets are only a representation for selection operation
}
