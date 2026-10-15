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

	public RequestRaw(Request request, String rawHeadBase64, String rawBodyBase64) {
		this.request = request;
		this.rawHeadBase64 = rawHeadBase64;
		this.rawBodyBase64 = rawBodyBase64;
	}

	@Id
	private Long id;

	@MapsId
	@OneToOne(optional = false, fetch = FetchType.LAZY)
	private Request request;

	// Base64 of the start line + headers + blank line
	@Column(nullable = false, columnDefinition = "text")
	private String rawHeadBase64;

	// Base64 of the body. Null when there is no body, or when it is stored as a file
	// (see Request.bodyStoredAsFile)
	@Column(columnDefinition = "text")
	private String rawBodyBase64;

}
