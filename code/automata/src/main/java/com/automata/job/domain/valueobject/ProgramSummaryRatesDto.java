package com.automata.job.domain.valueobject;

import lombok.Builder;

@Builder
public record ProgramSummaryRatesDto(int rateLimit, int currentRate, int currentWideRate) {

}
