package com.automata.program;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.program.enums.Platform;

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
public class Program {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	private String name;
	
	@Enumerated(EnumType.STRING)
	private Platform platform;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;
	
	
	
	
}
