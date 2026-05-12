package com.automata.request.comparator.common.dto;

import java.util.Optional;

public record ModifierPatchRequest(Optional<String> description, Optional<Boolean> isAvailable) {

}
