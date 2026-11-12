package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.automata.wordlist.common.dto.WordlistDetailedResponseDto;
import com.automata.wordlist.common.dto.WordlistSummaryResponseDto;

class WordlistMapperTest {

	private Wordlist wordlist() {
		Wordlist wordlist = new Wordlist();
		wordlist.setId(7L);
		wordlist.setName("Common");
		wordlist.setSmallDescription("s");
		wordlist.setPayloadContentDescription("p");
		wordlist.setNumberOfLines(2);
		wordlist.setSizeBytes(12L);
		wordlist.setSha256("a".repeat(64));
		return wordlist;
	}

	@Test
	void detailedResponseCarriesBothDescriptionsAndTheFileFacts() {
		assertEquals(new WordlistDetailedResponseDto(7L, "Common", "s", "p", 2, 12L, "a".repeat(64)),
				WordlistMapper.toDetailedResponse(wordlist()));
	}

	@Test
	void summaryResponseCarriesTheSmallDescriptionAndSizeButNotTheHash() {
		assertEquals(new WordlistSummaryResponseDto(7L, "Common", "s", 2, 12L),
				WordlistMapper.toSummaryResponse(wordlist()));
	}

	@Test
	void summaryResponseHasNoPayloadContentDescription() {
		List<String> fields = Arrays.stream(WordlistSummaryResponseDto.class.getRecordComponents())
				.map(RecordComponent::getName).toList();

		assertFalse(fields.contains("payloadContentDescription"));
	}

}
