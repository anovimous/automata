package com.automata.wordlist.common.dto;

import java.util.Optional;
import java.util.Set;

// The file of a wordlist (and what is taken from it) can not be changed. smallDescription and
// payloadContentDescription are mandatory: they can be changed but not removed (a blank value is a 400).
public record WordlistPatchRequest(Optional<String> name, Optional<String> smallDescription,
		Optional<String> payloadContentDescription, Optional<Set<Long>> vulnIds, boolean setNameNull) {

}
