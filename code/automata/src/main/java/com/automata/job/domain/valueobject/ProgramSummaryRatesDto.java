package com.automata.job.domain.valueobject;

import lombok.Builder;

@Builder
public record ProgramSummaryRatesDto(int rateLimit, Long currentRate, Long currentWideRate) {

}
