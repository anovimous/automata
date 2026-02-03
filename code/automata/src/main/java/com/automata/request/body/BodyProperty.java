package com.automata.request.body;

import org.springframework.lang.Nullable;

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

	public static BodyProperty of(String property, String value, PropertyValueType type, Long parentId) {
		BodyProperty bodyProperty = new BodyProperty();
		bodyProperty.setProperty(property);
		bodyProperty.setValue(value);
		bodyProperty.setPropertyValueType(type);
		bodyProperty.setParentId(parentId);
		return bodyProperty;

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Nullable
	private String property;

	@Nullable
	private String value;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PropertyValueType propertyValueType;

	private Long parentId;

	@ManyToOne
	private Request request;

}
