package com.automata.request.body;

import java.util.ArrayList;
import java.util.List;

import com.automata.request.Request;
import com.automata.request.common.enums.PropertyValueType;

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
public class BodyProperty {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(nullable = false)
	private String property;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PropertyValueType propertyValueType;
	
	private Long parentId;
	
	@ManyToOne
	private Request request;
	
	@OneToMany(mappedBy = "bodyProperty")
	private List<BodyPropertyValue> bodyPropertyValues = new ArrayList<>();
	
}
