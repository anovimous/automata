package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WordlistFileInfoTest {

	@TempDir
	Path tmp;

	private Path file(byte[] content) throws IOException {
		Path file = tmp.resolve("list.txt");
		Files.write(file, content);
		return file;
	}

	private Path file(String content) throws IOException {
		return file(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void hashSizeAndLinesOfAFile() throws IOException {
		WordlistFileInfo info = WordlistFileInfo.of(file("admin\nlogin\n"));

		assertEquals(64, info.sha256().length());
		assertEquals(12L, info.sizeBytes());
		assertEquals(2, info.numberOfLines());
	}

	@Test
	void sha256IsTheKnownDigestOfTheContent() throws IOException {
		// sha256("abc")
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
				WordlistFileInfo.of(file("abc")).sha256());
	}

	@Test
	void lastLineWithoutANewlineIsCounted() throws IOException {
		assertEquals(2, WordlistFileInfo.of(file("a\nb")).numberOfLines());
	}

	@Test
	void blankLinesAreLines() throws IOException {
		assertEquals(3, WordlistFileInfo.of(file("a\n\nb\n")).numberOfLines());
	}

	@Test
	void singleLineWithoutANewline() throws IOException {
		assertEquals(1, WordlistFileInfo.of(file("a")).numberOfLines());
	}

	@Test
	void fileLargerThanTheReadBufferIsReadWhole() throws IOException {
		byte[] content = new byte[200_000];
		java.util.Arrays.fill(content, (byte) 'a');
		for (int i = 99; i < content.length; i += 100)
			content[i] = '\n';

		WordlistFileInfo info = WordlistFileInfo.of(file(content));

		assertEquals(200_000L, info.sizeBytes());
		assertEquals(2000, info.numberOfLines());
	}

	@Test
	void sameContentGivesTheSameHash() throws IOException {
		String first = WordlistFileInfo.of(file("x\ny\n")).sha256();
		String second = WordlistFileInfo.of(file("x\ny\n")).sha256();

		assertEquals(first, second);
	}

	@Test
	void emptyFileIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> WordlistFileInfo.of(file("")));
	}

	@Test
	void fileWithANulByteIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> WordlistFileInfo.of(file(new byte[] { 'a', 0, 'b' })));
	}

	@Test
	void missingFileIsAnIoError() {
		assertThrows(IOException.class, () -> WordlistFileInfo.of(tmp.resolve("missing.txt")));
	}

}
