package com.automata.request.parameter;

import java.util.ArrayList;
import java.util.List;

import com.automata.request.Request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class QueryParameter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(nullable = false)
	private String parameter;
	
	@Column(nullable = false)
	@ManyToOne
	private Request request;
	
	@OneToMany(mappedBy = "queryParameter")
	private List<QueryParameterValue> queryParameterValues = new ArrayList<>();
	
	
}
