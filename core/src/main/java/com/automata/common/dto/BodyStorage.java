package com.automata.common.dto;

// Where the body of a stored request / response can be read from
public enum BodyStorage {

	// there is no body
	NONE,

	// the body is in the raw table (the /raw endpoint) as well as behind /body/stream
	INLINE,

	// the body is only available from /body/stream
	FILE

}
