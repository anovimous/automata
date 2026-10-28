package com.automata.job.api.dto;

import com.automata.job.domain.model.enums.HttpJobScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record HttpJobCreationRequest(@NotNull @Valid GenericHttpJobDetailsDto genericDetails,

		@NotNull HttpJobScope httpJobScope,

		@Valid NarrowHttpJobDetailsDto narrowJobDetails,

		@Valid WideHttpJobDetailsDto wideJobDetails) {

}
