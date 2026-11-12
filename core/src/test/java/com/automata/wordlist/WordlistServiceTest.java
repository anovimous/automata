package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class WordlistServiceTest {

	@Mock
	WordlistRepository wordlistRepo;
	@Mock
	VulnerabilityRepository vulnRepo;
	@Mock
	HttpJobRepository httpJobRepo;
	@Mock
	WordlistObjectStorage objectStorage;

	@InjectMocks
	WordlistService service;

	@TempDir
	Path tmp;

	private Path file(String content) throws IOException {
		Path file = tmp.resolve("upload.tmp");
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private void saveAssignsId(long id) {
		when(wordlistRepo.save(any())).thenAnswer(inv -> {
			Wordlist saved = inv.getArgument(0);
			saved.setId(id);
			return saved;
		});
	}

	private Wordlist existing() {
		Wordlist wordlist = new Wordlist();
		wordlist.setId(1L);
		wordlist.setName("old");
		wordlist.setSmallDescription("old small");
		wordlist.setPayloadContentDescription("old payload");
		wordlist.setSha256("a".repeat(64));
		wordlist.setSizeBytes(10L);
		wordlist.setNumberOfLines(2);
		return wordlist;
	}

	private WordlistPatchRequest patch(Optional<String> name, Optional<String> smallDescription,
			Optional<String> payloadContentDescription, boolean setNameNull) {
		return new WordlistPatchRequest(name, smallDescription, payloadContentDescription, Optional.empty(),
				setNameNull);
	}

	private WordlistPatchRequest nameOnly(Optional<String> name) {
		return patch(name, Optional.empty(), Optional.empty(), false);
	}

	// A request with the mandatory descriptions
	private WordlistCreationRequest described(String name, Set<Long> vulnIds) {
		return new WordlistCreationRequest(name, "small", "payload", vulnIds);
	}

	// create

	@Test
	void createWordlistTakesHashSizeAndLinesFromTheFile() throws IOException {
		saveAssignsId(5L);

		Wordlist created = service.createWordlist(file("abc"), "common.txt", described(null, null));

		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", created.getSha256());
		assertEquals(3L, created.getSizeBytes());
		assertEquals(1, created.getNumberOfLines());
	}

	@Test
	void createWordlistUploadsTheFileUnderTheSavedId() throws IOException {
		saveAssignsId(5L);
		Path file = file("a\nb\n");

		service.createWordlist(file, "common.txt", described(null, null));

		InOrder order = inOrder(wordlistRepo, objectStorage);
		order.verify(wordlistRepo).save(any());
		order.verify(objectStorage).store(5L, file);
	}

	@Test
	void createWordlistUsesTheNameAndDescriptionsOfTheRequest() throws IOException {
		saveAssignsId(5L);

		Wordlist created = service.createWordlist(file("a\n"), "upload.txt",
				new WordlistCreationRequest("Common paths", "Paths of web apps", "One path per line, no leading slash",
						null));

		assertEquals("Common paths", created.getName());
		assertEquals("Paths of web apps", created.getSmallDescription());
		assertEquals("One path per line, no leading slash", created.getPayloadContentDescription());
	}

	@Test
	void createWordlistWithoutANameUsesTheFileName() throws IOException {
		saveAssignsId(5L);

		Wordlist created = service.createWordlist(file("a\n"), "C:\\lists\\web/common.txt",
				described("  ", null));

		assertEquals("common.txt", created.getName());
	}

	@Test
	void createWordlistWithoutANameOrFileNameIsNamedWordlist() throws IOException {
		saveAssignsId(5L);

		Wordlist created = service.createWordlist(file("a\n"), null, described(null, null));

		assertEquals("wordlist", created.getName());
	}

	@Test
	void createWordlistWithoutMetadataIsRefusedForItsMissingDescriptions() throws IOException {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.createWordlist(file("a\n"), "a.txt", null));

		assertEquals("smallDescription is mandatory and must not be blank", ex.getMessage());
		verify(wordlistRepo, never()).save(any());
		verify(objectStorage, never()).store(any(), any());
	}

	@Test
	void createWordlistWithoutASmallDescriptionIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file("a\n"), "a.txt",
				new WordlistCreationRequest(null, null, "payload", null)));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistWithABlankSmallDescriptionIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file("a\n"), "a.txt",
				new WordlistCreationRequest(null, "  ", "payload", null)));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistWithoutAPayloadContentDescriptionIsRefused() throws IOException {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> service.createWordlist(file("a\n"), "a.txt", new WordlistCreationRequest(null, "small", null, null)));

		assertEquals("payloadContentDescription is mandatory and must not be blank", ex.getMessage());
		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistWithABlankPayloadContentDescriptionIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file("a\n"), "a.txt",
				new WordlistCreationRequest(null, "small", "\n", null)));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistLinksTheVulnerabilities() throws IOException {
		saveAssignsId(5L);
		Vulnerability vuln = new Vulnerability();
		when(vulnRepo.findAllById(Set.of(3L))).thenReturn(java.util.List.of(vuln));

		Wordlist created = service.createWordlist(file("a\n"), "a.txt", described(null, Set.of(3L)));

		assertEquals(Set.of(vuln), created.getVulnerabilities());
	}

	@Test
	void createWordlistOfAFileThatIsAlreadyAWordlistIsRefusedAndNothingIsStored() throws IOException {
		Wordlist same = existing();
		same.setId(9L);
		when(wordlistRepo.findBySha256("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"))
				.thenReturn(Optional.of(same));

		WordlistAlreadyExistsException ex = assertThrows(WordlistAlreadyExistsException.class,
				() -> service.createWordlist(file("abc"), "a.txt", described(null, null)));

		assertEquals("The file is already the wordlist 9", ex.getMessage());
		verify(wordlistRepo, never()).save(any());
		verify(objectStorage, never()).store(any(), any());
	}

	@Test
	void createWordlistWithAnEmptyFileIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file(""), "a.txt", described(null, null)));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistWithATooLongNameIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file("a\n"), "a.txt",
				described("n".repeat(Wordlist.MAX_NAME_LENGTH + 1), null)));

		verify(wordlistRepo, never()).save(any());
		verify(objectStorage, never()).store(any(), any());
	}

	@Test
	void createWordlistWithATooLongSmallDescriptionIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file("a\n"), "a.txt",
				new WordlistCreationRequest(null, "d".repeat(Wordlist.MAX_SMALL_DESCRIPTION_LENGTH + 1), "payload", null)));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistWithATooLongPayloadContentDescriptionIsRefused() throws IOException {
		assertThrows(IllegalArgumentException.class, () -> service.createWordlist(file("a\n"), "a.txt",
				new WordlistCreationRequest(null, "small",
						"d".repeat(Wordlist.MAX_PAYLOAD_CONTENT_DESCRIPTION_LENGTH + 1), null)));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void createWordlistAcceptsDescriptionsOfTheMaximumLength() throws IOException {
		saveAssignsId(5L);

		Wordlist created = service.createWordlist(file("a\n"), "a.txt",
				new WordlistCreationRequest(null, "s".repeat(Wordlist.MAX_SMALL_DESCRIPTION_LENGTH),
						"p".repeat(Wordlist.MAX_PAYLOAD_CONTENT_DESCRIPTION_LENGTH), null));

		assertEquals(500, created.getSmallDescription().length());
		assertEquals(2000, created.getPayloadContentDescription().length());
	}

	@Test
	void createWordlistWithAMissingFileIsAnIoFailure() {
		assertThrows(java.io.UncheckedIOException.class,
				() -> service.createWordlist(tmp.resolve("missing"), "a.txt", described(null, null)));

		verify(wordlistRepo, never()).save(any());
	}

	// nameOfFile

	@Test
	void nameOfFileIsTheLastPathPart() {
		assertEquals("a.txt", WordlistService.nameOfFile("/x/y/a.txt"));
		assertEquals("a.txt", WordlistService.nameOfFile("x\\a.txt"));
		assertEquals("a.txt", WordlistService.nameOfFile("a.txt"));
		assertEquals("wordlist", WordlistService.nameOfFile(null));
		assertEquals("wordlist", WordlistService.nameOfFile("/x/"));
	}

	// patch

	@Test
	void patchWordlistChangesNameAndDescriptions() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L,
				patch(Optional.of("new"), Optional.of("new small"), Optional.of("new payload"), false));

		assertEquals("new", patched.getName());
		assertEquals("new small", patched.getSmallDescription());
		assertEquals("new payload", patched.getPayloadContentDescription());
	}

	@Test
	void patchWordlistNeverChangesWhatComesFromTheFile() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L, nameOnly(Optional.of("new")));

		assertEquals("a".repeat(64), patched.getSha256());
		assertEquals(10L, patched.getSizeBytes());
		assertEquals(2, patched.getNumberOfLines());
		verify(objectStorage, never()).store(any(), any());
	}

	@Test
	void patchWordlistWithoutFieldsKeepsTheCurrentValues() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L, nameOnly(Optional.empty()));

		assertEquals("old", patched.getName());
		assertEquals("old small", patched.getSmallDescription());
		assertEquals("old payload", patched.getPayloadContentDescription());
	}

	@Test
	void patchWordlistCanRemoveTheNameButKeepsTheDescriptions() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L, patch(Optional.empty(), Optional.empty(), Optional.empty(), true));

		assertNull(patched.getName());
		assertEquals("old small", patched.getSmallDescription());
		assertEquals("old payload", patched.getPayloadContentDescription());
	}

	@Test
	void patchWordlistWithABlankSmallDescriptionIsRefusedAndNotSaved() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));

		assertThrows(IllegalArgumentException.class,
				() -> service.patchWordlist(1L, patch(Optional.empty(), Optional.of(" "), Optional.empty(), false)));

		assertEquals("old small", wordlist.getSmallDescription());
		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistWithABlankPayloadContentDescriptionIsRefusedAndNotSaved() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));

		assertThrows(IllegalArgumentException.class,
				() -> service.patchWordlist(1L, patch(Optional.empty(), Optional.empty(), Optional.of(""), false)));

		assertEquals("old payload", wordlist.getPayloadContentDescription());
		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistReplacesTheVulnerabilities() {
		Wordlist wordlist = existing();
		Vulnerability vuln = new Vulnerability();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));
		when(vulnRepo.findAllById(Set.of(4L))).thenReturn(java.util.List.of(vuln));
		when(wordlistRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wordlist patched = service.patchWordlist(1L,
				new WordlistPatchRequest(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(Set.of(4L)), false));

		assertEquals(Set.of(vuln), patched.getVulnerabilities());
	}

	@Test
	void patchWordlistWithATooLongSmallDescriptionIsRefusedAndNotSaved() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));

		assertThrows(IllegalArgumentException.class, () -> service.patchWordlist(1L, patch(Optional.empty(),
				Optional.of("d".repeat(Wordlist.MAX_SMALL_DESCRIPTION_LENGTH + 1)), Optional.empty(), false)));

		assertEquals("old small", wordlist.getSmallDescription());
		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistWithATooLongPayloadContentDescriptionIsRefusedAndNotSaved() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));

		assertThrows(IllegalArgumentException.class,
				() -> service.patchWordlist(1L, patch(Optional.empty(), Optional.empty(),
						Optional.of("d".repeat(Wordlist.MAX_PAYLOAD_CONTENT_DESCRIPTION_LENGTH + 1)), false)));

		assertEquals("old payload", wordlist.getPayloadContentDescription());
		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistWithATooLongNameIsRefusedAndNotSaved() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));

		assertThrows(IllegalArgumentException.class, () -> service.patchWordlist(1L,
				nameOnly(Optional.of("n".repeat(Wordlist.MAX_NAME_LENGTH + 1)))));

		verify(wordlistRepo, never()).save(any());
	}

	@Test
	void patchWordlistThatDoesNotExistIsNotFound() {
		when(wordlistRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class,
				() -> service.patchWordlist(1L, nameOnly(Optional.empty())));
	}

	// delete

	@Test
	void deleteWordlistDeletesTheRowAndThenTheFile() {
		Wordlist wordlist = existing();
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(wordlist));
		when(httpJobRepo.existsByWordlistId(1L)).thenReturn(false);

		service.deleteWordlist(1L);

		InOrder order = inOrder(wordlistRepo, objectStorage);
		order.verify(wordlistRepo).delete(wordlist);
		order.verify(objectStorage).deleteAfterCommit(1L);
	}

	@Test
	void deleteWordlistUsedByJobsIsRefusedAndKeepsTheFile() {
		when(wordlistRepo.findById(1L)).thenReturn(Optional.of(existing()));
		when(httpJobRepo.existsByWordlistId(1L)).thenReturn(true);

		assertThrows(WordlistInUseException.class, () -> service.deleteWordlist(1L));

		verify(wordlistRepo, never()).delete(any());
		verify(objectStorage, never()).deleteAfterCommit(any());
	}

	@Test
	void deleteWordlistThatDoesNotExistIsNotFound() {
		when(wordlistRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.deleteWordlist(1L));

		verify(objectStorage, never()).deleteAfterCommit(any());
	}

	// content

	@Test
	void getWordlistContentReadsTheFileFromStorage() {
		StoredBody body = StoredBody.inline(new byte[] { 1 }, "text/plain");
		when(wordlistRepo.existsById(1L)).thenReturn(true);
		when(objectStorage.download(1L)).thenReturn(body);

		assertEquals(body, service.getWordlistContent(1L));
	}

	@Test
	void getWordlistContentOfAWordlistThatDoesNotExistIsNotFound() {
		when(wordlistRepo.existsById(1L)).thenReturn(false);

		assertThrows(EntityNotFoundException.class, () -> service.getWordlistContent(1L));

		verify(objectStorage, never()).download(any());
	}

	@Test
	void createWordlistSavesWhatTheFileTold() throws IOException {
		saveAssignsId(5L);

		service.createWordlist(file("a\nb\nc"), "a.txt", described(null, null));

		ArgumentCaptor<Wordlist> saved = ArgumentCaptor.forClass(Wordlist.class);
		verify(wordlistRepo).save(saved.capture());
		assertEquals(3, saved.getValue().getNumberOfLines());
		assertEquals(5L, saved.getValue().getSizeBytes());
	}

}
