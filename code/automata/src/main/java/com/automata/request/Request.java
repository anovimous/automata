package com.automata.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;
import com.automata.request.body.BodyProperty;
import com.automata.request.enums.ContentType;
import com.automata.request.enums.Method;
import com.automata.request.enums.Source;
import com.automata.request.parameter.QueryParameter;
import com.automata.request.path.PathVariable;
import com.automata.request.tenant.Tenant;

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
public class Request {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Source source;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Method method;
	
	@Column(nullable = false)
	private String computatedPath;
	
	private String extension;
	
	@OneToMany(mappedBy = "request")
	private List<PathVariable> pathVariables = new ArrayList<>();
	
	private Integer numberOfProperties;
	
	// For now, request will only support JSON and POST form data
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ContentType contentType;
	
	@OneToMany(mappedBy = "request")
	private List<BodyProperty> bodyProperties = new ArrayList<>();
	
	@ManyToOne
	private Tenant tenant;
	
	@OneToMany(mappedBy = "request")
	private List<QueryParameter> parameters = new ArrayList<>();
	
	@Column(nullable = false)
	@ManyToOne
	private Host host;
	
	
}
