package com.automata.wordlist.common.dto;

import java.util.Set;

// The properties of a wordlist that do not come from its file; the file is the other part of the
// request. name falls back to the file name.
public record WordlistCreationRequest(String name, String description, Set<Long> vulnIds) {

}
