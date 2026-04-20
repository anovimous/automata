package com.automata.job.api.dto;

import com.automata.job.domain.model.enums.HttpJobScope;

public record HttpJobCreationRequest(GenericHttpJobDetailsDto genericDetails,

		HttpJobScope httpJobScope,

		NarrowHttpJobDetailsDto narrowJobDetails,

		WideHttpJobDetailsDto wideJobDetails,

		GlobalHttpJobDetailsDto globalJobDetails) {

}