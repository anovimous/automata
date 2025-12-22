package com.automata.routine;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import com.automata.routine.enums.Duration;
import com.automata.routine.enums.Overhead;
import com.automata.routine.enums.PermittedScope;
import com.automata.routine.enums.Protocol;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Routine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(nullable = false)
	private String name;
	
	private String description;
	
	@UpdateTimestamp
	private Instant updatedAt;
	
	private boolean isAvailableAtConsumer;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Overhead overhead;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Protocol protocol;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PermittedScope scope;
	
	@Enumerated(EnumType.STRING)
	private Duration duration;
	
	@ManyToOne
	private Vulnerability vulnerability;
	
}
