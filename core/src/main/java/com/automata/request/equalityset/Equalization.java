package com.automata.request.equalityset;

import java.time.Instant;

import com.automata.host.Host;
import com.automata.request.comparator.Comparator;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Equalization {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Instant lastRun;

	private boolean autoEqualize;

	@ManyToOne(optional = false)
	private Comparator comparator;

	@ManyToOne(optional = false)
	public Host host;

}
