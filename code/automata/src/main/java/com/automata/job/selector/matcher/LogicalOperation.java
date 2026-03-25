package com.automata.job.selector.matcher;

public enum LogicalOperation {

	AND {

		@Override
		public boolean apply(boolean first, boolean second) {
			return first && second;
		}

	},
	OR {

		@Override
		public boolean apply(boolean first, boolean second) {
			return first || second;
		}
	};

	public abstract boolean apply(boolean first, boolean second);

}
