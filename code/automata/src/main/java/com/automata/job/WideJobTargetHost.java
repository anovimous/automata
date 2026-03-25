package com.automata.job;

import com.automata.host.Host;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
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

}
