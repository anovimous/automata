package com.automata.response;

import com.automata.host.Host;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class ResponseBodyProperty {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(nullable = false)
	private String property;
	
	private int parentId;
	
	@Column(nullable = false)
	@ManyToOne
	private Response response;
	
	@Column(nullable = false)
	@ManyToOne
	private Host host;
	
}
