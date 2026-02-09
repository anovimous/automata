package com.automata.request.equalityset;

import java.util.HashSet;
import java.util.Set;

import com.automata.host.Host;
import com.automata.request.Request;
import com.automata.request.comparator.Comparator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class RequestEqualitySet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Integer currentNumberOfRequests;

	@ManyToOne(optional = false)
	private Comparator comparator;

	@ManyToOne(optional = false)
	public Host host;

	@ManyToMany
	private Set<Request> requests = new HashSet<>();

}
