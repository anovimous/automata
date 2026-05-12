package com.automata.request.header;

import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import com.automata.host.Host;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@RequiredArgsConstructor
@Getter
@Setter
public class Header {

	@EmbeddedId
	private HeaderCompositePK primaryKey;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDate insertionDate;

	private Location location;

	@MapsId("hostId")
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private Host host;
}
