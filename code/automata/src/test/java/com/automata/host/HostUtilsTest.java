package com.automata.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.automata.testsupport.KnownBug;

class HostUtilsTest {

	@ParameterizedTest
	@ValueSource(strings = { "example.com", "api.example.com", "a.b.c.example.org", "xn--bcher-kva.de", "example.co.uk",
			"127.0.0.1", "10.0.0.255", "my-host.example.com", "EXAMPLE.com" })
	void validHostFormats(String host) {
		assertTrue(HostUtils.validateHostFormat(host).isValid(), host);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "-bad.example.com", "bad-.example.com", "exa mple.com", "example..com", ".example.com",
			"http://example.com", "example.com/path", "example.com:8080", "999.999.999.999", "under_score.example.com" })
	void invalidHostFormats(String host) {
		assertFalse(HostUtils.validateHostFormat(host).isValid(), host);
	}

	@Test
	@KnownBug("BUG-013")
	void aSingleLabelNameIsNotAValidHost() {
		assertFalse(HostUtils.validateHostFormat("example").isValid());
		assertFalse(HostUtils.validateHostFormat("localhost").isValid());
	}

	@Test
	void levelIsZeroForARootDomain() {
		assertEquals(0, HostUtils.identifyLevel("example.com"));
	}

	@Test
	void levelGrowsWithEverySubdomainLabel() {
		assertEquals(1, HostUtils.identifyLevel("api.example.com"));
		assertEquals(2, HostUtils.identifyLevel("v1.api.example.com"));
		assertEquals(3, HostUtils.identifyLevel("a.v1.api.example.com"));
	}

	@Test
	@KnownBug("BUG-013")
	void levelOfAMultiLabelPublicSuffixRootDomainIsZero() {
		assertEquals(0, HostUtils.identifyLevel("example.co.uk"));
	}

	@Test
	@KnownBug("BUG-013")
	void levelOfAnIpAddressIsNotADomainDepth() {
		assertEquals(0, HostUtils.identifyLevel("10.0.0.1"));
	}

	@Test
	void rateLimitsAreValidWhenTheHostLimitCoversLongPlusShort() {
		assertTrue(HostUtils.validateRateLimits(10, 6, 4).isValid());
		assertTrue(HostUtils.validateRateLimits(10, 0, 0).isValid());
		assertTrue(HostUtils.validateRateLimits(0, 0, 0).isValid());
	}

	@Test
	void rateLimitsAreInvalidWhenLongPlusShortExceedTheHostLimit() {
		assertFalse(HostUtils.validateRateLimits(10, 6, 5).isValid());
		assertFalse(HostUtils.validateRateLimits(0, 1, 0).isValid());
	}
}
