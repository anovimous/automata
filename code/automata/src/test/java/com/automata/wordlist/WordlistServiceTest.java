package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.vulnerability.VulnerabilityRepository;
import com.automata.wordlist.common.dto.WordlistPatchRequest;

@ExtendWith(MockitoExtension.class)
class WordlistServiceTest {

	@Mock
	WordlistRepository wordlistRepo;
	@Mock
	VulnerabilityRepository vulnRepo;
	@Spy
	WordlistPathPolicy pathPolicy = new WordlistPathPolicy("/home/haidar/cbr/wordlists");

	@InjectMocks
	WordlistService service;

	private Wordlist wordlist(String path) {
		Wordlist wordlist = new Wordlist();
		wordlist.setPath(path);
		return wordlist;
	}

	private WordlistPatchRequest patchPath(String path) {
		return new WordlistPatchRequest(Optional.of(path), Optional.empty(), Optional.empty(), Optional.empty(), false,
				false);
	}

	@Test
	void createWordlistStoresTheNormalizedPath() {
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist created = service.createWordlist(wordlist("/home/haidar/cbr/wordlists/a/../ids.txt"), Set.of());

		assertEquals("/home/haidar/cbr/wordlists/ids.txt", created.getPath());
	}

	@Test
	void createWordlistOutsideTheBaseDirectoryIsRefusedAndNotSaved() {
		assertThrows(IllegalArgumentException.class,
				() -> service.createWordlist(wordlist("/etc/passwd"), Set.of()));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistWithoutAPathIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(wordlist(null), Set.of()));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistStoresTheNormalizedPath() {
		Wordlist existing = wordlist("/home/haidar/cbr/wordlists/old.txt");
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(existing));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L, patchPath("/home/haidar/cbr/wordlists/web/./new.txt"));

		assertEquals("/home/haidar/cbr/wordlists/web/new.txt", patched.getPath());
	}

	@Test
	void patchWordlistToAPathOutsideTheBaseDirectoryIsRefusedAndNotSaved() {
		Wordlist existing = wordlist("/home/haidar/cbr/wordlists/old.txt");
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(existing));

		assertThrows(IllegalArgumentException.class,
				() -> service.patchWordlist(1L, patchPath("/home/haidar/cbr/wordlists/../../.ssh/id_rsa")));

		assertEquals("/home/haidar/cbr/wordlists/old.txt", existing.getPath());
		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistWithoutAPathKeepsTheCurrentOne() {
		Wordlist existing = wordlist("/home/haidar/cbr/wordlists/old.txt");
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(existing));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L, new WordlistPatchRequest(Optional.empty(), Optional.of("name"),
				Optional.empty(), Optional.empty(), false, false));

		assertEquals("/home/haidar/cbr/wordlists/old.txt", patched.getPath());
		assertEquals("name", patched.getName());
	}

}
