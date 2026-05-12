package com.automata.program.common.dto;

import java.util.Set;

import org.springframework.lang.Nullable;

import com.automata.program.common.enums.Platform;

public record PatchProgramRequest(String name,@Nullable String link, Integer programRateLimit,@Nullable Platform platform,
		Set<Long> outOfScopeVulnIds, boolean setLinkNull, boolean setPlatformNull) {
}
