package com.automata.request.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BurpRequestAdditionDto(@NotNull String requestBase64, @NotBlank String host, String base64Response) {

}
