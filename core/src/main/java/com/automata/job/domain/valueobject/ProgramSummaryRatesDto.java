package com.automata.job.domain.valueobject;

import lombok.Builder;

@Builder
public record ProgramSummaryRatesDto(int rateLimit, Long currentRate, Long currentWideRate) {

	/**
	 * Returns the rates after a job of the given rate has been accepted. A wide job
	 * also adds to the wide rate; the total rate includes both kinds.
	 */
	public ProgramSummaryRatesDto withAcceptedJob(int jobRate, boolean wide) {
		return new ProgramSummaryRatesDto(rateLimit, currentRate + jobRate,
				wide ? currentWideRate + jobRate : currentWideRate);
	}

}
