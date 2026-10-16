package com.automata.job.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.program.Program;
import com.automata.routine.Routine;
import com.automata.wordlist.Wordlist;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
public abstract class HttpJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// makes a write based on a stale copy of the job fail instead of overwriting
	// what was written in between
	@Version
	private Long version;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant creationDate;

	@Embedded
	private HttpJobGenericDetails genericDetails;

	@Embedded
	private JobResult result;

	@ManyToOne
	private Program program;

	@ManyToOne(optional = false)
	private Routine routine;

	@ManyToMany
	private List<Wordlist> wordlists = new ArrayList<>();

}
