package com.automata.response;

import jakarta.validation.constraints.NotNull;

public record RawResponseAdditionDto(@NotNull String responseBase64) {

}
