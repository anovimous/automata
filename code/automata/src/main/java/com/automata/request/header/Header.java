package com.automata.request.header;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Header {

	@EmbeddedId
	private HeaderCompositePK headerCompositeKeyObject;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;	

	
}
