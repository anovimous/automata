package com.automata.wordlist.common.dto;

import java.util.Optional;
import java.util.Set;

public record WordlistPatchRequest(Optional<String> path, Optional<String> name, Optional<Integer> numberOfLines, Optional<Set<Long>> vulnIds, boolean setNameNull, boolean setNumberOfLinesNull) {

}
