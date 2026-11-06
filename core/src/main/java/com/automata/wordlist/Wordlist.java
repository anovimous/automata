package com.automata.wordlist;

import java.util.HashSet;
import java.util.Set;

import com.automata.vulnerability.Vulnerability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

// The file of a wordlist is an object in the wordlists bucket (key = id) and never changes:
// sha256, sizeBytes and numberOfLines are set from it when the wordlist is created. Only
// name, description and vulnerabilities can be changed afterwards.
@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Wordlist {

	public static final int MAX_NAME_LENGTH = 255;

	public static final int MAX_DESCRIPTION_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@Column(length = MAX_DESCRIPTION_LENGTH)
	private String description;

	@Column(nullable = false)
	private Integer numberOfLines;

	@Column(nullable = false)
	private Long sizeBytes;

	@Column(nullable = false, unique = true, length = 64)
	private String sha256;

	@ManyToMany
	private Set<Vulnerability> vulnerabilities = new HashSet<>();
}
