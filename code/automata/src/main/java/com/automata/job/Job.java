package com.automata.job;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;
import com.automata.job.authentication.Authentication;
import com.automata.job.enums.JobState;
import com.automata.job.enums.ResultsVerbosity;
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
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private JobState state;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ResultsVerbosity verbosity;
	
	//1->3:Lazy prioritized,4:Eager (5: reserved for retried eager runs)
	@Column(nullable = false)
	private Integer priority;
	
	//Nullable for non-HTTP
	private Integer rate;
	
	// Config will be in a seperate config file, including targets
	
	@ManyToOne
	private Routine routine;
	
	@ManyToOne
	private Authentication authentication;
	
	@ManyToOne
	private Program program;
	
	@ManyToOne
	private Host host;
}
