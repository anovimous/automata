package com.automata.wordlist;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;
import com.automata.wordlist.common.dto.WordlistPatchRequest;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WordlistService {

	private final WordlistRepository wordlistRepo;

	private final VulnerabilityRepository vulnRepo;

	public Wordlist getWordlistById(Long wordlistId) {
		return wordlistRepo.findById(wordlistId).orElseThrow(() -> new EntityNotFoundException("Wordlist not found"));
	}

	public Page<Wordlist> getWordlistsPagedAndFilteredOnQueryString(String name, Pageable pageable) {

		return wordlistRepo.findByNameContainingIgnoreCase(name, pageable);

	}

	public Page<Wordlist> getVulnerabilityWordlistsPagedAndFilteredOnQueryString(Long vulnId, String name,
			Pageable pageable) {

		Vulnerability vuln = vulnRepo.findById(vulnId)
				.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

		return wordlistRepo.findByNameContainingIgnoreCaseAndVulnerabilities(name, vuln, pageable);
	}

	public Wordlist createWordlist(Wordlist toBeCreatedWordlist, Set<Long> vulnIds) {

		if (vulnIds != null && !vulnIds.isEmpty()) {

			Set<Vulnerability> vulns = vulnRepo.findAllById(vulnIds).stream().collect(Collectors.toSet());
			toBeCreatedWordlist.setVulnerabilities(vulns);

		}

		return wordlistRepo.save(toBeCreatedWordlist);
	}

	public Wordlist patchWordlist(Long wordlistId, WordlistPatchRequest patchRequest) {

		Wordlist wordlist = wordlistRepo.findById(wordlistId)
				.orElseThrow(() -> new EntityNotFoundException("Wordlist not found"));

		if (patchRequest.setNameNull())
			wordlist.setName(null);
		else
			patchRequest.name().ifPresent(wordlist::setName);

		if (patchRequest.setNumberOfLinesNull())
			wordlist.setNumberOfLines(null);
		else
			patchRequest.numberOfLines().ifPresent(wordlist::setNumberOfLines);

		patchRequest.path().ifPresent(wordlist::setPath);

		patchRequest.vulnIds().ifPresent((vulnsIds) -> {

			Set<Vulnerability> vulns = vulnRepo.findAllById(vulnsIds).stream().collect(Collectors.toSet());

			wordlist.setVulnerabilities(vulns);

		});

		return wordlistRepo.save(wordlist);

	}

	public void deleteWordlist(Long wordlistId) {

		Wordlist wordlist = wordlistRepo.findById(wordlistId)
				.orElseThrow(() -> new EntityNotFoundException("Wordlist not found"));

		wordlistRepo.delete(wordlist);

	}

}
