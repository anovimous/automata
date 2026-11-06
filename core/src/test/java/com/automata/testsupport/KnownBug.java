package com.automata.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Disabled;

/**
 * Marks a test that asserts the CORRECT behaviour of something that is currently broken. The bug is described, with its suggested fix, in
 * {@code .claude/functionality-bugs.md} under the given id. The test is disabled so the suite stays green; once the bug is fixed, remove this annotation.
 * To see the tests fail again: {@code ./mvnw test -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'}.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Disabled("Known bug, see .claude/functionality-bugs.md")
public @interface KnownBug {

	/** The id of the bug in .claude/functionality-bugs.md, for instance "BUG-001". */
	String value();
}
