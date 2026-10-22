package com.automata.agentcontext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.host.Host;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentContext {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, updatable = false)
	@Builder.Default
	private UUID uuid = UUID.randomUUID();

	@Column(nullable = false)
	private String name;

	private String description;

	@ManyToOne(optional = false)
	private Host host;

	@Column(nullable = false)
	@Builder.Default
	private boolean defaultAutoApproval = false;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private RepresentativeStrategy representativeStrategy;

	@ManyToMany
	@Builder.Default
	private List<Routine> allowedRoutines = new ArrayList<>();

	// Tenants (identities of the host, with their authentication) the agent can
	// test with, all of the context's host
	@ManyToMany
	@Builder.Default
	private List<Tenant> tenants = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	private Duration defaultDuration;

	@Enumerated(EnumType.STRING)
	private ResultsVerbosity defaultVerbosity;

	private Integer defaultPriority;

	private Integer maxRate;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant creationDate;

	@OneToMany(mappedBy = "context")
	@Builder.Default
	private List<AgentContextItem> items = new ArrayList<>();

}
