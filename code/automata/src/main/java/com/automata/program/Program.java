package com.automata.program;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

import com.automata.program.common.enums.Platform;
import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Program {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;
	
	@Column(nullable = false)
	private String name;
	
	@Nullable
	private String link;
	
	private int programRateLimit;
	
	@Nullable
	@Enumerated(EnumType.STRING)
	private Platform platform;
	
	@ManyToMany
	private Set<Vulnerability> outOfScopeVulns = new HashSet<>();
	
	
	
	
}
