package com.automata.request.common.dto;

import jakarta.validation.constraints.NotNull;

public record RequestsEqualizationDto(@NotNull Long hostId, @NotNull Long comparatorId, boolean autoEqualize) {

}
