package com.automata.job.domain.model;

import com.automata.host.Host;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class WideJobTargetHost {

	public static WideJobTargetHost of(WideHttpJob job, Host targetHost) {

		WideJobTargetHost instance = new WideJobTargetHost();
		instance.setJob(job);
		instance.setTargetHost(targetHost);
		return instance;

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	private WideHttpJob job;

	@ManyToOne
	private Host targetHost;

	// Identity-based equality: the associations are lazy and must not take part in equals/hashCode
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof WideJobTargetHost other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
