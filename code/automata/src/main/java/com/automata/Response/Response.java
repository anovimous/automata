package com.automata.Response;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;
import com.automata.request.Request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class Response {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;
	
	// For now, response will only support JSON responses
	
	@OneToMany(mappedBy = "response")
	private List<ResponseBodyProperty> bodyProperties = new ArrayList<>();
	
	@ManyToOne
	private Request request;
	
	@Column(nullable = false)
	@ManyToOne
	private Host host;
	
}
