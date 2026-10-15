package com.automata.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Kept in its own table, sharing the Request's id, so the (possibly large) raw payload
// is only loaded when explicitly asked for.
@Entity
@NoArgsConstructor
@Getter
@Setter
public class RequestRaw {

	public RequestRaw(Request request, String rawBase64) {
		this.request = request;
		this.rawBase64 = rawBase64;
	}

	@Id
	private Long id;

	@MapsId
	@OneToOne(optional = false, fetch = FetchType.LAZY)
	private Request request;

	// Base64 of the original request, exactly as received
	@Column(nullable = false, columnDefinition = "text")
	private String rawBase64;

}
