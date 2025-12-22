package com.automata.request.path;

import java.util.ArrayList;
import java.util.List;

import com.automata.request.Request;
import com.automata.request.enums.PathVariableValueType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class PathVariable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(nullable = false)
	private Integer index;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PathVariableValueType type;
	
	@Column(nullable = false)
	@ManyToOne
	private Request request;
	
	@OneToMany(mappedBy = "pathVariable")
	private List<PathVariableValue> pathVariableValues = new ArrayList<>();
}
