package com.automata.host;

import org.springframework.data.domain.Page;

import com.automata.host.common.dto.HostDetailedResponse;
import com.automata.host.common.dto.HostSummaryResponse;

public abstract class HostMapper {

	public static HostDetailedResponse toDetailedResponse(Host host) {

		return HostDetailedResponse.builder().id(host.getId()).insertionDate(host.getInsertionDate())
				.host(host.getHost()).level(host.getLevel()).scope(host.getScope()).outOfScope(host.isOutOfScope())
				.hostRateLimit(host.getHostRateLimit()).shortRateLimit(host.getShortRateLimit())
				.longRateLimit(host.getLongRateLimit()).programId(host.getProgram().getId()).build();

	}

	public static Page<HostSummaryResponse> toSummaryResponse(Page<Host> hosts) {

		return hosts.map(HostMapper::toSummaryResponse);

	}

	public static HostSummaryResponse toSummaryResponse(Host host) {

		return HostSummaryResponse.builder().id(host.getId()).insertionDate(host.getInsertionDate())
				.host(host.getHost()).level(host.getLevel()).scope(host.getScope()).outOfScope(host.isOutOfScope())
				.build();

	}

}
