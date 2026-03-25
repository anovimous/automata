package com.automata.job.common.dto;

import com.automata.job.common.enums.HttpJobScope;

public record HttpJobCreationRequest(GenericHttpJobDetailsDto genericDetails,

		HttpJobScope httpJobScope,

		NarrowHttpJobDetailsDto narrowJobDetails,

		WideHttpJobDetailsDto wideJobDetails,

		GlobalHttpJobDetailsDto globalJobDetails) {

}