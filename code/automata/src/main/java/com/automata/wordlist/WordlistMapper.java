package com.automata.wordlist;

import org.springframework.data.domain.Page;

import com.automata.wordlist.common.dto.WordlistDetailedResponseDto;
import com.automata.wordlist.common.dto.WordlistSummaryResponseDto;

public abstract class WordlistMapper {

	public static WordlistDetailedResponseDto toDetailedResponse(Wordlist wordlist) {
		return WordlistDetailedResponseDto.builder().id(wordlist.getId()).name(wordlist.getName())
				.path(wordlist.getPath()).numberOfLines(wordlist.getNumberOfLines()).build();
	}

	public static Page<WordlistSummaryResponseDto> toSummaryResponse(Page<Wordlist> wordlists) {
		return wordlists.map(WordlistMapper::toSummaryResponse);
	}

	public static WordlistSummaryResponseDto toSummaryResponse(Wordlist wordlist) {
		return WordlistSummaryResponseDto.builder().id(wordlist.getId()).name(wordlist.getName())
				.numberOfLines(wordlist.getNumberOfLines()).build();
	}

}
