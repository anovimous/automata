package com.automata.job.domain.valueobject;

import com.automata.wordlist.Wordlist;

// A wordlist as the worker gets it in the details file: it downloads the file from the wordlists
// bucket (key = id), checks sha256 and sizeBytes and keeps it by sha256
public record WorkerWordlist(Long id, String name, String sha256, Long sizeBytes) {

	public static WorkerWordlist of(Wordlist wordlist) {
		return new WorkerWordlist(wordlist.getId(), wordlist.getName(), wordlist.getSha256(), wordlist.getSizeBytes());
	}

}
