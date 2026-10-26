package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WordlistPathPolicyTest {

	private final WordlistPathPolicy policy = new WordlistPathPolicy("/home/haidar/cbr/wordlists");

	@Test
	void aFileInsideTheBaseDirectoryIsAccepted() {
		assertEquals("/home/haidar/cbr/wordlists/common.txt",
				policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/common.txt"));
	}

	@Test
	void aFileInASubdirectoryIsAccepted() {
		assertEquals("/home/haidar/cbr/wordlists/web/ids.txt",
				policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/web/ids.txt"));
	}

	@Test
	void theStoredPathIsNormalized() {
		assertEquals("/home/haidar/cbr/wordlists/b.txt",
				policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/a/../b.txt"));
		assertEquals("/home/haidar/cbr/wordlists/b.txt",
				policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists//./b.txt"));
	}

	@Test
	void aTrailingSlashOnTheBaseDirectoryChangesNothing() {
		assertEquals("/home/haidar/cbr/wordlists/b.txt", new WordlistPathPolicy("/home/haidar/cbr/wordlists/")
				.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/b.txt"));
	}

	@Test
	void aPathEscapingTheBaseDirectoryWithDotsIsRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/../../.ssh/id_rsa"));
		assertThrows(IllegalArgumentException.class,
				() -> policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/../secret.txt"));
	}

	@Test
	void aPathOutsideTheBaseDirectoryIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> policy.requireInsideBaseDirectory("/etc/passwd"));
	}

	@Test
	void aSiblingDirectoryWithTheSamePrefixIsRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists-private/x.txt"));
	}

	@Test
	void theBaseDirectoryItselfIsRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists"));
		assertThrows(IllegalArgumentException.class,
				() -> policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/"));
	}

	@Test
	void aRelativePathIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> policy.requireInsideBaseDirectory("wordlists/common.txt"));
		assertThrows(IllegalArgumentException.class, () -> policy.requireInsideBaseDirectory("../common.txt"));
	}

	@Test
	void aMissingOrBlankPathIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> policy.requireInsideBaseDirectory(null));
		assertThrows(IllegalArgumentException.class, () -> policy.requireInsideBaseDirectory("  "));
	}

	@Test
	void aPathWithANulCharacterIsRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> policy.requireInsideBaseDirectory("/home/haidar/cbr/wordlists/a\u0000b.txt"));
	}

	@Test
	void aMissingBaseDirectoryStopsTheStartup() {
		assertThrows(IllegalStateException.class, () -> new WordlistPathPolicy(null));
		assertThrows(IllegalStateException.class, () -> new WordlistPathPolicy(" "));
	}

	@Test
	void aRelativeBaseDirectoryStopsTheStartup() {
		assertThrows(IllegalStateException.class, () -> new WordlistPathPolicy("cbr/wordlists"));
	}

}
