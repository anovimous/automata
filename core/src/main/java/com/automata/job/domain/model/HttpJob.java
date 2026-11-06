package com.automata.job.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.agentcontext.AgentContext;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.enums.JobCreator;
import com.automata.program.Program;
import com.automata.routine.Routine;
import com.automata.wordlist.Wordlist;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Enumerated;
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

	// the lengths of the ai_description and ai_result columns (see the Flyway migrations); longer
	// values are rejected before they reach the database
	public static final int MIN_RATE = 1;

	public static final int MIN_PRIORITY = 1;

	public static final int MAX_PRIORITY = 5;

	public static final int MAX_AI_DESCRIPTION_LENGTH = 1000;

	public static final int MAX_AI_RESULT_LENGTH = 5000;

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

	@Column(length = MAX_AI_DESCRIPTION_LENGTH)
	private String aiDescription;

	@Column(length = MAX_AI_RESULT_LENGTH)
	private String aiResult;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	@lombok.Builder.Default
	private JobCreator createdBy = JobCreator.USER;

	@Column(nullable = false)
	@lombok.Builder.Default
	private boolean approved = true;

	@ManyToOne(fetch = FetchType.LAZY)
	private AgentContext agentContext;

}
