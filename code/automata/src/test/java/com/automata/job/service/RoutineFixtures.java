package com.automata.job.service;

import java.util.EnumSet;

import com.automata.routine.Routine;
import com.automata.routine.common.enums.TargetCardinalityPair;

// A routine the core accepts for a job of any kind of target: it is available at the worker
// and it allows every target
final class RoutineFixtures {

	private RoutineFixtures() {
	}

	static Routine usableRoutine() {
		Routine routine = new Routine();
		routine.setAvailableAtConsumer(true);
		routine.setAllowedTargets(EnumSet.allOf(TargetCardinalityPair.class));
		return routine;
	}

}
