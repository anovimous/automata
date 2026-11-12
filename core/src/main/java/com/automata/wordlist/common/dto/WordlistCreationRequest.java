package com.automata.wordlist.common.dto;

import java.util.Set;

// The properties of a wordlist that do not come from its file; the file is the other part of the
// request. name falls back to the file name; smallDescription and payloadContentDescription are
// mandatory (missing or blank is a 400), so the metadata part is too.
public record WordlistCreationRequest(String name, String smallDescription, String payloadContentDescription,
		Set<Long> vulnIds) {

}
