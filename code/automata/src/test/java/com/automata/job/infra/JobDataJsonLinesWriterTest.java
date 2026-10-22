package com.automata.job.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.automata.job.domain.valueobject.HostData;
import com.automata.job.domain.valueobject.RequestData;

class JobDataJsonLinesWriterTest {

	@TempDir
	Path tmpDir;

	@Test
	void closingWritesTheBufferedLinesToTheFile() throws IOException {
		Path file = tmpDir.resolve("job");

		JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(file.toString());
		writer.writeHostData(new HostData(1L, "a.com", null));
		writer.writeRequestData(new RequestData(2L, "cmF3"));
		writer.close();

		List<String> lines = Files.readAllLines(file);
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("\"host\":\"a.com\""));
		assertTrue(lines.get(1).contains("\"requestId\":2"));
	}

	@Test
	void tryWithResourcesClosesTheWriter() throws IOException {
		Path file = tmpDir.resolve("job");
		JobDataJsonLinesWriter used;

		try (JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(file.toString())) {
			writer.writeRequestData(new RequestData(3L, "cmF3"));
			used = writer;
		}

		assertEquals(1, Files.readAllLines(file).size());
		assertThrows(IOException.class, () -> used.writeRequestData(new RequestData(4L, "cmF3")));
	}

	@Test
	void closingTwiceIsHarmless() throws IOException {
		JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(tmpDir.resolve("job").toString());

		writer.close();
		writer.close();
	}

	@Test
	void writingToAnExistingFileReplacesItsContent() throws IOException {
		Path file = tmpDir.resolve("job");
		Files.writeString(file, "stale line from an earlier attempt\n");

		try (JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(file.toString())) {
			writer.writeRequestData(new RequestData(5L, "cmF3"));
		}

		List<String> lines = Files.readAllLines(file);
		assertEquals(1, lines.size());
		assertTrue(lines.get(0).contains("\"requestId\":5"));
	}

}
