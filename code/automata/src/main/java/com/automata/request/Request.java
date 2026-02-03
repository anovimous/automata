package com.automata.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.body.BodyProperty;
import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.parameter.QueryParameter;
import com.automata.request.path.PathVariable;
import com.automata.tenant.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Request {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Method method;

	@Column(nullable = false)
	private String computatedPath;

	@Nullable
	private String extension;

	// This is automatically set, equals bodyProperties.length()
	private int numberOfProperties;

	// For now, request will only support JSON and POST form data

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ContentType contentType;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Source source;

	@Column(nullable = false)
	@ManyToOne
	private Program program;

	@Column(nullable = false)
	@ManyToOne
	private Host host;

	@Nullable
	@ManyToOne
	private Tenant tenant;

	@OneToMany(fetch = FetchType.EAGER, mappedBy = "request")
	private List<PathVariable> pathVariables = new ArrayList<>();

	@OneToMany(mappedBy = "request")
	private List<BodyProperty> bodyProperties = new ArrayList<>();

	@OneToMany(mappedBy = "request")
	private List<QueryParameter> parameters = new ArrayList<>();

	@ManyToMany
	private List<RequestEqualitySet> equalitySet = new ArrayList<>();

}
