package com.automata.request.body;

import com.automata.request.Request;
import com.automata.request.common.enums.PropertyValueType;
import com.automata.response.Response;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class BodyProperty {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String fullPath;

	private String value;

	private Boolean isArrayElement;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PropertyValueType propertyValueType;

	@ManyToOne
	private Request request;

	@ManyToOne
	private Response response;

}
