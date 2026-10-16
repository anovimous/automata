package com.automata.job.domain.model;

import com.automata.request.Request;

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
public class NarrowJobTargetRequest {

	public static NarrowJobTargetRequest of(NarrowHttpJob job, Request targetRequest) {
		NarrowJobTargetRequest relation = new NarrowJobTargetRequest();
		relation.setJob(job);
		relation.setTargetRequest(targetRequest);
		return relation;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	private NarrowHttpJob job;

	@ManyToOne
	private Request targetRequest;

	// Identity-based equality: the associations are lazy and must not take part in equals/hashCode
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof NarrowJobTargetRequest other))
			return false;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
