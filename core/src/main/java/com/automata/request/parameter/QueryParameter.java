package com.automata.request.parameter;

import com.automata.request.common.RequestFieldLimits;
import com.automata.request.Request;
import com.automata.request.valueanalysis.ValueAnalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class QueryParameter {

	public static QueryParameter of(String parameter, String value) {
		QueryParameter queryParam = new QueryParameter();
		queryParam.setParameter(parameter);
		queryParam.setValue(value);
		return queryParam;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = RequestFieldLimits.MAX_PARAMETER_NAME)
	private String parameter;

	@Column(nullable = false, length = RequestFieldLimits.MAX_PARAMETER_VALUE)
	private String value;

	@ManyToOne(optional = false)
	private Request request;

	@OneToOne
	private ValueAnalysis valueAnalysis;

}
