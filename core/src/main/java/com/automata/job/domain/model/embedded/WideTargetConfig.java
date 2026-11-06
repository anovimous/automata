package com.automata.job.domain.model.embedded;

import java.util.List;

import com.automata.job.domain.model.WideJobTargetHost;

import jakarta.persistence.Embeddable;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WideTargetConfig {

	@OneToMany(mappedBy = "job")
	private List<WideJobTargetHost> targetHosts;

}
