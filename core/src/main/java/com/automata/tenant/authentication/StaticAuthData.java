package com.automata.tenant.authentication;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaticAuthData {

	private StaticData data;

	private Instant lifeTimeInSeconds;

	private Instant populatedAt;

}
