package com.automata.request.header;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class HeaderCompositePK implements Serializable {

	private static final long serialVersionUID = 6699915325160645145L;

	@Column(nullable = false)
	private String header;

	private Long hostId;

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof HeaderCompositePK))
			return false;
		HeaderCompositePK that = (HeaderCompositePK) o;
		return Objects.equals(header, that.header) && Objects.equals(hostId, that.hostId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(header, hostId);
	}
}
