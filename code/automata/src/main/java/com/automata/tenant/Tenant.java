package com.automata.tenant;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class Tenant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@ManyToOne(optional = false)
	private Host host;

}
