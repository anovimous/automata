package com.automata.request.body;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.request.valueversion.RequestValuesVersion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class BodyPropertyValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;
	
	@Column(nullable = false)
	private String value;
	
	@Column(nullable = false)
	@ManyToOne
	private BodyProperty bodyProperty;
	
	@Column(nullable = false)
	@ManyToOne
	private RequestValuesVersion requestValuesVersion;
	
}
