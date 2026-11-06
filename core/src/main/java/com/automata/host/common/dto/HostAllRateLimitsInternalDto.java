package com.automata.host.common.dto;

public record HostAllRateLimitsInternalDto(Long hostId, Integer rateLimit, Integer shortRateLimit,
		Integer longRateLimit) {

}
