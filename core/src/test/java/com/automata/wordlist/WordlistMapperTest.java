package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.automata.wordlist.common.dto.WordlistDetailedResponseDto;
import com.automata.wordlist.common.dto.WordlistSummaryResponseDto;

class WordlistMapperTest {

	private Wordlist wordlist() {
		Wordlist wordlist = new Wordlist();
		wordlist.setId(7L);
		wordlist.setName("Common");
		wordlist.setDescription("d");
		wordlist.setNumberOfLines(2);
		wordlist.setSizeBytes(12L);
		wordlist.setSha256("a".repeat(64));
		return wordlist;
	}

	@Test
	void detailedResponseCarriesTheFileFacts() {
		assertEquals(new WordlistDetailedResponseDto(7L, "Common", "d", 2, 12L, "a".repeat(64)),
				WordlistMapper.toDetailedResponse(wordlist()));
	}

	@Test
	void summaryResponseCarriesTheDescriptionAndSizeButNotTheHash() {
		assertEquals(new WordlistSummaryResponseDto(7L, "Common", "d", 2, 12L),
				WordlistMapper.toSummaryResponse(wordlist()));
	}

}
