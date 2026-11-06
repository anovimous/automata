package com.automata.wordlist;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.automata.common.dto.response.PageHolderResponse;
import com.automata.wordlist.common.dto.WordlistCreationRequest;
import com.automata.wordlist.common.dto.WordlistDetailedResponseDto;
import com.automata.wordlist.common.dto.WordlistPatchRequest;
import com.automata.wordlist.common.dto.WordlistSummaryResponseDto;
import com.automata.storage.BodyDownloads;
import com.automata.storage.StoredBody;

@RestController
@RequestMapping("/api/wordlists")
public class WordlistController {

	private final WordlistService wordlistService;

	private final Path tmpDir;

	public WordlistController(WordlistService wordlistService,
			@Value("${com.automata.files.tmp.location}") String tmpDir) {
		this.wordlistService = wordlistService;
		this.tmpDir = Path.of(tmpDir);
	}

	@GetMapping("/{wordlistId}")
	public ResponseEntity<WordlistDetailedResponseDto> getWordlist(@PathVariable Long wordlistId) {

		Wordlist wordlist = wordlistService.getWordlistById(wordlistId);

		return ResponseEntity.ok(WordlistMapper.toDetailedResponse(wordlist));

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<WordlistSummaryResponseDto>> getWordlists(
			@RequestParam(required = false) Long vulnId, @RequestParam(defaultValue = "") String query,
			@PageableDefault(size = 30, sort = { "name", "id" }, direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Wordlist> wordlists;

		if (vulnId == null)
			wordlists = wordlistService.getWordlistsPagedAndFilteredOnQueryString(query, pageable);
		else
			wordlists = wordlistService.getVulnerabilityWordlistsPagedAndFilteredOnQueryString(vulnId, query, pageable);

		Page<WordlistSummaryResponseDto> wordlistResponses = WordlistMapper.toSummaryResponse(wordlists);

		return ResponseEntity.ok(new PageHolderResponse<>(wordlistResponses));

	}

	// multipart: the file of the wordlist is the part "file", its other properties the optional JSON part "metadata"
	@PostMapping(value = "", consumes = "multipart/form-data")
	public ResponseEntity<Void> createWordlist(@RequestPart("file") MultipartFile file,
			@RequestPart(value = "metadata", required = false) WordlistCreationRequest metadata) throws IOException {

		Files.createDirectories(tmpDir);
		Path spooled = Files.createTempFile(tmpDir, "wordlist-", ".upload");

		try {

			file.transferTo(spooled);

			Wordlist createdWordlist = wordlistService.createWordlist(spooled, file.getOriginalFilename(), metadata);

			return ResponseEntity.status(201)
					.location(URI.create(String.format("/api/wordlists/%d", createdWordlist.getId()))).build();

		} finally {
			Files.deleteIfExists(spooled);
		}

	}

	@GetMapping("/{wordlistId}/content")
	public ResponseEntity<InputStreamResource> getWordlistContent(@PathVariable Long wordlistId) {

		StoredBody content = wordlistService.getWordlistContent(wordlistId);

		return BodyDownloads.attachment(content, String.format("wordlist-%d.txt", wordlistId));

	}

	@PatchMapping("/{wordlistId}")
	public ResponseEntity<Void> patchWordlist(@PathVariable Long wordlistId,
			@RequestBody WordlistPatchRequest patchRequest) {

		wordlistService.patchWordlist(wordlistId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{wordlistId}")
	public ResponseEntity<Void> deleteWordlist(@PathVariable Long wordlistId) {

		wordlistService.deleteWordlist(wordlistId);

		return ResponseEntity.status(204).build();

	}

}
