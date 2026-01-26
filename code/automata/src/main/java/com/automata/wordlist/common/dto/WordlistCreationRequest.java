package com.automata.wordlist.common.dto;

import java.util.Set;

public record WordlistCreationRequest(String path, String name, Integer numberOfLines, Set<Long> vulnIds) {

}
