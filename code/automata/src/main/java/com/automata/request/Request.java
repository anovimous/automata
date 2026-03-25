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
import com.automata.response.Response;
import com.automata.tenant.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class Request {

	public static Request of(Long id) {
		Request req = new Request();
		req.setId(id);
		return req;
	}

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

	// Includes the Protocol
	private String version;

	// This is automatically set, equals bodyProperties.length()-1
	private int numberOfProperties;

	// For now, request will only support JSON and POST form data

	@Enumerated(EnumType.STRING)
	private ContentType contentType;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Source source;

	@ManyToOne(optional = false)
	private Program program;

	@ManyToOne(optional = false)
	private Host host;

	@ManyToOne
	private Tenant tenant;

	@OneToOne
	private Response response;

	@OneToMany(mappedBy = "request")
	private List<PathVariable> pathVariables = new ArrayList<>();

	@OneToMany(mappedBy = "request")
	private List<BodyProperty> bodyProperties = new ArrayList<>();

	@OneToMany(mappedBy = "request")
	private List<QueryParameter> parameters = new ArrayList<>();

	@ManyToMany(mappedBy = "requests")
	private List<RequestEqualitySet> equalitySets = new ArrayList<>();

}
