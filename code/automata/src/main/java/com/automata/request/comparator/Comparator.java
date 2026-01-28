package com.automata.comparator;

import java.util.HashSet;
import java.util.Set;

import com.automata.comparator.enums.Modifier;
import com.automata.comparator.enums.Schema;
import com.automata.host.Host;
import com.automata.program.Program;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ElementCollection;
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
	private long id;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Schema schema;
	
	@ElementCollection(targetClass = Modifier.class)
    @CollectionTable(
        name = "comparator_modifiers",
        joinColumns = @JoinColumn(name = "comparator_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "modifier")
	private Set<Modifier> modifiers =  new HashSet<>();;
	
	//If both program and host are null then it is global
	//They can only be specified on creation, not allowed on update
	
	//nullable
	@ManyToOne
	private Program program;
	
	//nullable
	@ManyToOne
	private Host host;
}
