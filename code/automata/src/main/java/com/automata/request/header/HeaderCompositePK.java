package com.automata.request.header;

import java.io.Serializable;

import com.automata.host.Host;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.ManyToOne;

@Embeddable
public class HeaderCompositePK implements Serializable{

	private static final long serialVersionUID = 6699915325160645145L;
	
	@Column(nullable = false)
	private String header;
	
	@ManyToOne
	@Column(nullable = false)
	private Host host;

}
