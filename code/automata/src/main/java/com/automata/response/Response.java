package com.automata.response;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;
import com.automata.request.Request;
import com.automata.request.body.BodyProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
public class Response {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;

	// For now, response will only support JSON responses

	@Column(nullable = false)
	private Integer statusCode;

	@Enumerated(EnumType.STRING)
	private ResponseContentType contentType;

	@Column(nullable = false)
	private Integer contentLength;

	@OneToOne(mappedBy = "response")
	private Request request;

	@ManyToOne(optional = false)
	private Host host;

	@Builder.Default
	@OneToMany(mappedBy = "response")
	private List<BodyProperty> bodyProperties = new ArrayList<>();

}
