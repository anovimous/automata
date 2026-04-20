package com.automata.routine;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

import com.automata.routine.common.enums.Overhead;
import com.automata.routine.common.enums.PermittedScope;
import com.automata.routine.common.enums.Protocol;
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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Routine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false, updatable = false, unique = true)
	private String key;

	@Nullable
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

	@Nullable
	@ManyToOne
	private Vulnerability vulnerability;

}
