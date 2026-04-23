package com.automata.wordlist;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.automata.common.dto.response.PageHolderResponse;
import com.automata.wordlist.common.dto.WordlistCreationRequest;
import com.automata.wordlist.common.dto.WordlistPatchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wordlists")
@RequiredArgsConstructor
public class WordlistController {

	private final WordlistService wordlistService;

	private final ObjectMapper mapper;

	@GetMapping("/{wordlistId}")
	public ResponseEntity<Wordlist> getWordlist(@PathVariable Long wordlistId) {

		Wordlist wordlist = wordlistService.getWordlistById(wordlistId);

		return ResponseEntity.ok(wordlist);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<Wordlist>> getWordlists(@RequestParam(required = false) Long vulnId,
			@RequestParam(defaultValue = "") String query,
			@PageableDefault(size = 30, sort = { "name", "path" }, direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Wordlist> wordlists;

		if (vulnId == null)
			wordlists = wordlistService.getWordlistsPagedAndFilteredOnQueryString(query, pageable);
		else
			wordlists = wordlistService.getVulnerabilityWordlistsPagedAndFilteredOnQueryString(vulnId, query, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(wordlists));

	}

	@PostMapping("")
	public ResponseEntity<Wordlist> createWordlist(@RequestBody WordlistCreationRequest request) {

		Wordlist toBeCreatedWordlist = mapper.convertValue(request, Wordlist.class);

		Set<Long> vulnIds = request.vulnIds();

		Wordlist createdWordlist = wordlistService.createWordlist(toBeCreatedWordlist, vulnIds);

		return ResponseEntity.status(201).body(createdWordlist);

	}

	@PatchMapping("/{wordlistId}")
	public ResponseEntity<Wordlist> patchWordlist(@PathVariable Long wordlistId,
			@RequestBody WordlistPatchRequest patchRequest) {

		Wordlist wordlist = wordlistService.patchWordlist(wordlistId, patchRequest);

		return ResponseEntity.ok(wordlist);
	}

	@DeleteMapping("/{wordlistId}")
	public ResponseEntity<Void> deleteWordlist(@PathVariable Long wordlistId) {

		wordlistService.deleteWordlist(wordlistId);

		return ResponseEntity.status(204).build();

	}

}
