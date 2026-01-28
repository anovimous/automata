package com.automata.request.comparator;

import java.util.HashSet;
import java.util.Set;

import org.springframework.lang.Nullable;

import com.automata.host.Host;
import com.automata.program.Program;
import com.automata.request.comparator.common.enums.Schema;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Comparator {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Nullable
	@Enumerated(EnumType.STRING)
	private Schema schema;

	@ManyToMany
	@JoinTable(name = "comparator_modifier", joinColumns = @JoinColumn(name = "comparator_id"), inverseJoinColumns = @JoinColumn(name = "modifier_id"))
	private Set<Modifier> modifiers = new HashSet<>();

	// NOTE: If both program and host are null then it is global and can be used
	// globally and is not tied to a specific entity
	// They can only be specified on creation, not allowed on update

	@Nullable
	@ManyToOne
	private Program program;

	@Nullable
	@ManyToOne
	private Host host;
}
