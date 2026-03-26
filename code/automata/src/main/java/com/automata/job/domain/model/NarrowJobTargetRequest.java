package com.automata.job.domain.model;

import com.automata.request.Request;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
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

}
