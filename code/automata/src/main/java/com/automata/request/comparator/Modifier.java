package com.automata.request.comparator;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.request.comparator.common.enums.Schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Modifier {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false)
	private String key;

	@Column(nullable = false)
	private String description;
	
	private boolean isAvailable;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Schema schema;
}
