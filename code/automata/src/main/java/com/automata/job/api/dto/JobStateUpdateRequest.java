package com.automata.job.api.dto;

import com.automata.job.domain.model.enums.JobState;

// failureCategory is the name of a FailureCategory, sent by the worker only with a failure; a name the core
// does not know is read as UNKNOWN
public record JobStateUpdateRequest(Long jobId, JobState newState, String message, String failureCategory) {

}
