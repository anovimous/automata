package com.automata.host;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.automata.host.common.enums.Scope;
import com.automata.program.Program;

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
public class Host {

	public static Host of(Long hostId) {
		Host host = new Host();
		host.setId(hostId);
		return host;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;

	@Column(nullable = false, unique = true)
	private String host;

	// 0 for root domains, incremental for others
	@Column(nullable = false)
	private Integer level;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Scope scope;

	private boolean outOfScope;

	private int hostRateLimit;

	private int shortRateLimit;

	private int longRateLimit;

	@ManyToOne
	private Program program;

	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<String> technologies;

	private String notes;

}
