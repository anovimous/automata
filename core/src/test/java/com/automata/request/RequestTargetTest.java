package com.automata.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestTargetTest {

	// ---- origin-form ----

	@Test
	void aPathWithoutQueryHasNoQuery() {
		RequestTarget target = RequestTarget.parse("/api/users");

		assertEquals("/api/users", target.path());
		assertNull(target.query());
	}

	@Test
	void thePathAndTheQueryAreSplitAtTheFirstQuestionMark() {
		RequestTarget target = RequestTarget.parse("/api/users?a=1&b=2");

		assertEquals("/api/users", target.path());
		assertEquals("a=1&b=2", target.query());
	}

	@Test
	void aSecondQuestionMarkBelongsToTheQuery() {
		RequestTarget target = RequestTarget.parse("/a?next=/b?c=1");

		assertEquals("/a", target.path());
		assertEquals("next=/b?c=1", target.query());
	}

	@Test
	void anEmptyQueryIsEmptyAndNotNull() {
		assertEquals("", RequestTarget.parse("/api?").query());
	}

	@Test
	void theRootPathIsKept() {
		assertEquals("/", RequestTarget.parse("/").path());
		assertEquals("/", RequestTarget.parse("/?x=1").path());
	}

	@Test
	void aFragmentIsNotPartOfThePathOrTheQuery() {
		assertEquals("/a", RequestTarget.parse("/a#top").path());
		assertNull(RequestTarget.parse("/a#top").query());
		assertEquals("x=1", RequestTarget.parse("/a?x=1#top").query());
	}

	@Test
	void aQuestionMarkInTheFragmentIsNotAQuery() {
		RequestTarget target = RequestTarget.parse("/a#frag?x=1");

		assertEquals("/a", target.path());
		assertNull(target.query());
	}

	// ---- what java.net.URI refuses ----

	@Test
	void charactersWhichBrowsersSendUnencodedInTheQueryAreKept() {
		RequestTarget target = RequestTarget.parse("/api?filter[name]=x|y&ids={1}&q=a^b\\c\"d<e>f`g");

		assertEquals("/api", target.path());
		assertEquals("filter[name]=x|y&ids={1}&q=a^b\\c\"d<e>f`g", target.query());
	}

	@Test
	void charactersWhichJavaNetUriRefusesInThePathAreKept() {
		assertEquals("/a[b]/c|d^e/{id}/f\\g\"h<i>j`k", RequestTarget.parse("/a[b]/c|d^e/{id}/f\\g\"h<i>j`k").path());
	}

	@Test
	void percentEscapesAreKeptAsSentEvenWhenTheyAreMalformed() {
		assertEquals("/my%20file/100%25/50%/%zz", RequestTarget.parse("/my%20file/100%25/50%/%zz").path());
		assertEquals("a=100%&b=%zz", RequestTarget.parse("/a?a=100%&b=%zz").query());
	}

	@Test
	void aPathWhichStartsWithTwoSlashesIsAPathAndNotAnAuthority() {
		assertEquals("//example.com/a", RequestTarget.parse("//example.com/a").path());
	}

	@Test
	void nonAsciiCharactersAreKept() {
		assertEquals("/café", RequestTarget.parse("/café").path());
	}

	// ---- absolute-form ----

	@Test
	void theSchemeAndTheAuthorityOfAnAbsoluteUrlAreDropped() {
		RequestTarget target = RequestTarget.parse("http://example.com/api/users?a=1");

		assertEquals("/api/users", target.path());
		assertEquals("a=1", target.query());
	}

	@Test
	void anAbsoluteUrlWithAPortAndUserInfoLosesThemToo() {
		assertEquals("/a", RequestTarget.parse("https://user:pw@example.com:8443/a").path());
		assertEquals("/a", RequestTarget.parse("http://[::1]:8080/a").path());
	}

	@Test
	void anAbsoluteUrlWithoutAPathAsksForTheRoot() {
		assertEquals("/", RequestTarget.parse("http://example.com").path());
		assertEquals("/", RequestTarget.parse("http://example.com?x=1").path());
		assertEquals("x=1", RequestTarget.parse("http://example.com?x=1").query());
	}

	@Test
	void theSpecialCharactersOfAnAbsoluteUrlAreKeptToo() {
		RequestTarget target = RequestTarget.parse("http://example.com/a[b]?f[x]=1|2");

		assertEquals("/a[b]", target.path());
		assertEquals("f[x]=1|2", target.query());
	}

	// ---- what is refused ----

	@ParameterizedTest
	@ValueSource(strings = { "foo", "foo/bar", "*", "example.com:443", "?x=1", "ftp:/a" })
	void aTargetWhichIsNeitherAPathNorAnAbsoluteUrlIsRefused(String target) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RequestTarget.parse(target));

		assertTrue(e.getMessage().contains("must be a path starting with '/' or an absolute URL"), e.getMessage());
	}

	@Test
	void anEmptyOrMissingTargetIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> RequestTarget.parse(""));
		assertThrows(IllegalArgumentException.class, () -> RequestTarget.parse(null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/a b", "/a\tb", "/a\nb", "/a\rb", "/a\u0000b", "/a\u0001b", "/a\u007fb", "/a?x=\r\nHost: evil" })
	void aSpaceOrAControlCharacterIsRefusedSinceItWouldBeWrittenIntoARequestLine(String target) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RequestTarget.parse(target));

		assertTrue(e.getMessage().contains("contains a space or a control character"), e.getMessage());
	}

	@Test
	void theMessageCutsAVeryLongTarget() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> RequestTarget.parse("x".repeat(500)));

		assertTrue(e.getMessage().length() < 200, e.getMessage());
	}

}
