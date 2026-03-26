package com.automata.job.domain.policy;

public enum PredicateOperation {
	EQUALS {
		@Override
		public boolean apply(String actual, String expected) {
			return actual.equals(expected);
		}
	},
	CONTAINS {
		@Override
		public boolean apply(String actual, String expected) {
			return actual.contains(expected);
		}
	},
	NEQUALS {
		@Override
		public boolean apply(String actual, String expected) {
			return !actual.equals(expected);
		}
	},
	NCONTAINS {
		@Override
		public boolean apply(String actual, String expected) {
			return !actual.contains(expected);
		}
	};

	public abstract boolean apply(String actual, String expected);
}
