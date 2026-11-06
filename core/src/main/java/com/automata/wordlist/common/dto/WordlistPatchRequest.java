package com.automata.wordlist.common.dto;

import java.util.Optional;
import java.util.Set;

// The file of a wordlist (and what is taken from it) can not be changed
public record WordlistPatchRequest(Optional<String> name, Optional<String> description, Optional<Set<Long>> vulnIds,
		boolean setNameNull, boolean setDescriptionNull) {

}
