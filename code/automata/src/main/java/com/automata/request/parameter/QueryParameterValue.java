package com.automata.request.parameter;

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
public class QueryParameterValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(nullable = false)
	private String value;
	
	@Column(nullable = false)
	@ManyToOne
	private QueryParameter queryParameter;
	
	@Column(nullable = false)
	@ManyToOne
	private RequestValuesVersion requestValuesVersion;
	
	
}
