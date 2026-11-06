package com.automata.wordlist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.job.repository.HttpJobRepository;
import com.automata.storage.StoredBody;
import com.automata.storage.WordlistObjectStorage;
import com.automata.vulnerability.Vulnerability;
import com.automata.vulnerability.VulnerabilityRepository;
import com.automata.wordlist.common.dto.WordlistCreationRequest;
import com.automata.wordlist.common.dto.WordlistPatchRequest;
import com.automata.wordlist.common.exception.WordlistAlreadyExistsException;
import com.automata.wordlist.common.exception.WordlistInUseException;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WordlistService {

	private final WordlistRepository wordlistRepo;

	private final VulnerabilityRepository vulnRepo;

	private final HttpJobRepository httpJobRepo;

	private final WordlistObjectStorage objectStorage;

	public Wordlist getWordlistById(Long wordlistId) {
		return wordlistRepo.findById(wordlistId).orElseThrow(() -> new EntityNotFoundException("Wordlist not found"));
	}

	public Page<Wordlist> getWordlistsPagedAndFilteredOnQueryString(String name, Pageable pageable) {

		return wordlistRepo.findByNameContainingIgnoreCase(name, pageable);

	}

	@Transactional(readOnly = true)
	public Page<Wordlist> getVulnerabilityWordlistsPagedAndFilteredOnQueryString(Long vulnId, String name,
			Pageable pageable) {

		Vulnerability vuln = vulnRepo.findById(vulnId)
				.orElseThrow(() -> new EntityNotFoundException("Vulnerability not found"));

		return wordlistRepo.findByNameContainingIgnoreCaseAndVulnerabilities(name, vuln, pageable);
	}

	// The file is what the wordlist is: its hash, size and lines are read from it here and never
	// change. fileName (the name the file was uploaded with) is the name when the request has none.
	@Transactional
	public Wordlist createWordlist(Path file, String fileName, WordlistCreationRequest request) {

		WordlistFileInfo info;
		try {
			info = WordlistFileInfo.of(file);
		} catch (IOException e) {
			throw new UncheckedIOException("The wordlist file could not be read", e);
		}

		wordlistRepo.findBySha256(info.sha256()).ifPresent(existing -> {
			throw new WordlistAlreadyExistsException("The file is already the wordlist " + existing.getId());
		});

		String name = request != null && request.name() != null && !request.name().isBlank() ? request.name()
				: nameOfFile(fileName);
		String description = request != null ? request.description() : null;

		requireValidName(name);
		requireValidDescription(description);

		Wordlist toBeCreatedWordlist = new Wordlist();
		toBeCreatedWordlist.setName(name);
		toBeCreatedWordlist.setDescription(description);
		toBeCreatedWordlist.setSha256(info.sha256());
		toBeCreatedWordlist.setSizeBytes(info.sizeBytes());
		toBeCreatedWordlist.setNumberOfLines(info.numberOfLines());

		if (request != null && request.vulnIds() != null && !request.vulnIds().isEmpty()) {

			Set<Vulnerability> vulns = vulnRepo.findAllById(request.vulnIds()).stream().collect(Collectors.toSet());
			toBeCreatedWordlist.setVulnerabilities(vulns);

		}

		// saved first, the key of the file is the id; the file is deleted again if the transaction fails
		Wordlist created = wordlistRepo.save(toBeCreatedWordlist);

		objectStorage.store(created.getId(), file);

		return created;
	}

	// The file of the wordlist, to be closed by the reader
	public StoredBody getWordlistContent(Long wordlistId) {

		if (!wordlistRepo.existsById(wordlistId))
			throw new EntityNotFoundException("Wordlist not found");

		return objectStorage.download(wordlistId);
	}

	@Transactional
	public Wordlist patchWordlist(Long wordlistId, WordlistPatchRequest patchRequest) {

		Wordlist wordlist = wordlistRepo.findById(wordlistId)
				.orElseThrow(() -> new EntityNotFoundException("Wordlist not found"));

		if (patchRequest.setNameNull())
			wordlist.setName(null);
		else
			patchRequest.name().ifPresent(name -> {
				requireValidName(name);
				wordlist.setName(name);
			});

		if (patchRequest.setDescriptionNull())
			wordlist.setDescription(null);
		else
			patchRequest.description().ifPresent(description -> {
				requireValidDescription(description);
				wordlist.setDescription(description);
			});

		patchRequest.vulnIds().ifPresent((vulnsIds) -> {

			Set<Vulnerability> vulns = vulnRepo.findAllById(vulnsIds).stream().collect(Collectors.toSet());

			wordlist.setVulnerabilities(vulns);

		});

		return wordlistRepo.save(wordlist);

	}

	@Transactional
	public void deleteWordlist(Long wordlistId) {

		Wordlist wordlist = wordlistRepo.findById(wordlistId)
				.orElseThrow(() -> new EntityNotFoundException("Wordlist not found"));

		if (httpJobRepo.existsByWordlistId(wordlistId))
			throw new WordlistInUseException("The wordlist is used by jobs and can not be deleted");

		wordlistRepo.delete(wordlist);

		objectStorage.deleteAfterCommit(wordlistId);

	}

	// The last part of the path the file was uploaded with
	static String nameOfFile(String fileName) {

		if (fileName == null)
			return "wordlist";

		String name = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1).trim();

		return name.isEmpty() ? "wordlist" : name;
	}

	private static void requireValidName(String name) {
		if (name != null && name.length() > Wordlist.MAX_NAME_LENGTH)
			throw new IllegalArgumentException("name is longer than " + Wordlist.MAX_NAME_LENGTH + " characters");
	}

	private static void requireValidDescription(String description) {
		if (description != null && description.length() > Wordlist.MAX_DESCRIPTION_LENGTH)
			throw new IllegalArgumentException(
					"description is longer than " + Wordlist.MAX_DESCRIPTION_LENGTH + " characters");
	}

}
