package com.automata.job.domain.model;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;
import com.automata.job.domain.model.enums.JobState;
import com.automata.program.Program;
import com.automata.routine.Routine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@Getter
@Setter
public class NetworkJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private JobState state;

	@ManyToOne(optional = false)
	private Routine routine;

	@ManyToOne(optional = false)
	private Program program;

	@ManyToOne(optional = false)
	private Host host;

}
