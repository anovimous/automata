package com.automata.request.path;

import com.automata.request.Request;
import com.automata.request.common.enums.PathVariableValueType;

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
public class PathVariable {

	public static PathVariable of(Integer index, PathVariableValueType type, String value) {

		PathVariable var = new PathVariable();
		var.setIndex(index);
		var.setType(type);
		var.setValue(value);
		return var;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Integer index;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PathVariableValueType type;

	@Column(nullable = false)
	private String value;

	@Column(nullable = false)
	@ManyToOne
	private Request request;

}
