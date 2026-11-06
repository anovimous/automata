package com.automata.job.api.dto;

import com.automata.job.domain.model.enums.JobState;

public record JobPollDto(JobState requestedState) {

}
