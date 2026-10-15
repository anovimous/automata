package com.automata.request.comparator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.lang.Nullable;

import com.automata.request.comparator.common.enums.Schema;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Comparator {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate creationDate;

	@Column(nullable = false)
	private String name;

	@Nullable
	@Enumerated(EnumType.STRING)
	private Schema schema;

	@ManyToMany
	@JoinTable(name = "comparator_modifier", joinColumns = @JoinColumn(name = "comparator_id"), inverseJoinColumns = @JoinColumn(name = "modifier_id"))
	@Builder.Default
	private List<Modifier> modifiers = new ArrayList<>();

}
