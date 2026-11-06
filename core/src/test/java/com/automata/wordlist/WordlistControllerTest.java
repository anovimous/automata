package com.automata.wordlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.automata.storage.StoredBody;
import com.automata.wordlist.common.dto.WordlistCreationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WordlistControllerTest {

	@Mock
	WordlistService wordlistService;

	@TempDir
	Path tmp;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new WordlistController(wordlistService, tmp.resolve("spool").toString()))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()), new ResourceHttpMessageConverter())
				.build();
	}

	private Wordlist created(long id) {
		Wordlist wordlist = new Wordlist();
		wordlist.setId(id);
		return wordlist;
	}

	@Test
	void createHandsTheUploadedFileAndTheMetadataToTheService() throws Exception {
		List<String> contentSeen = new ArrayList<>();
		when(wordlistService.createWordlist(any(), any(), any())).thenAnswer(inv -> {
			contentSeen.add(Files.readString(inv.<Path>getArgument(0), StandardCharsets.UTF_8));
			return created(7L);
		});

		mvc.perform(multipart("/api/wordlists")
				.file(new MockMultipartFile("file", "common.txt", "text/plain", "admin\nlogin\n".getBytes()))
				.file(new MockMultipartFile("metadata", "", "application/json",
						"{\"name\":\"Common\",\"description\":\"d\",\"vulnIds\":[3]}".getBytes())))
				.andExpect(status().isCreated()).andExpect(header().string("Location", "/api/wordlists/7"));

		ArgumentCaptor<WordlistCreationRequest> metadata = ArgumentCaptor.forClass(WordlistCreationRequest.class);
		verify(wordlistService).createWordlist(any(), eq("common.txt"), metadata.capture());
		assertEquals(new WordlistCreationRequest("Common", "d", Set.of(3L)), metadata.getValue());
		assertEquals(List.of("admin\nlogin\n"), contentSeen);
	}

	@Test
	void createWithoutMetadataPassesNull() throws Exception {
		when(wordlistService.createWordlist(any(), any(), any())).thenReturn(created(7L));

		mvc.perform(multipart("/api/wordlists").file(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes())))
				.andExpect(status().isCreated());

		verify(wordlistService).createWordlist(any(), eq("a.txt"), eq(null));
	}

	@Test
	void createRemovesTheSpooledFileAfterwards() throws Exception {
		List<Path> spooled = new ArrayList<>();
		when(wordlistService.createWordlist(any(), any(), any())).thenAnswer(inv -> {
			spooled.add(inv.getArgument(0));
			return created(7L);
		});

		mvc.perform(multipart("/api/wordlists").file(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes())))
				.andExpect(status().isCreated());

		assertEquals(1, spooled.size());
		assertFalse(Files.exists(spooled.get(0)));
	}

	@Test
	void createRemovesTheSpooledFileWhenTheServiceRefuses() throws Exception {
		List<Path> spooled = new ArrayList<>();
		when(wordlistService.createWordlist(any(), any(), any())).thenAnswer(inv -> {
			spooled.add(inv.getArgument(0));
			throw new IllegalArgumentException("the file is empty");
		});

		try {
			mvc.perform(multipart("/api/wordlists").file(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes())));
		} catch (Exception expected) {
			// no advice in the standalone setup, the exception is thrown to the caller
		}

		assertEquals(1, spooled.size());
		assertFalse(Files.exists(spooled.get(0)));
	}

	@Test
	void createWithoutTheFilePartIsABadRequest() throws Exception {
		mvc.perform(multipart("/api/wordlists").file(new MockMultipartFile("metadata", "", "application/json", "{}".getBytes())))
				.andExpect(status().isBadRequest());

		verify(wordlistService, never()).createWordlist(any(), any(), any());
	}

	@Test
	void createWithAJsonBodyIsUnsupported() throws Exception {
		mvc.perform(post("/api/wordlists").contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"/x\"}"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void contentIsStreamedAsAnAttachment() throws Exception {
		when(wordlistService.getWordlistContent(7L)).thenReturn(StoredBody.inline("a\nb\n".getBytes(), "text/plain"));

		mvc.perform(get("/api/wordlists/7/content")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/plain"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"wordlist-7.txt\""))
				.andExpect(content().string("a\nb\n"));
	}

	@Test
	void getReturnsTheDetailsWithoutAnyPath() throws Exception {
		Wordlist wordlist = created(7L);
		wordlist.setName("Common");
		wordlist.setDescription("d");
		wordlist.setNumberOfLines(2);
		wordlist.setSizeBytes(12L);
		wordlist.setSha256("a".repeat(64));
		when(wordlistService.getWordlistById(7L)).thenReturn(wordlist);

		mvc.perform(get("/api/wordlists/7")).andExpect(status().isOk())
				.andExpect(content().json("{\"id\":7,\"name\":\"Common\",\"description\":\"d\",\"numberOfLines\":2,"
						+ "\"sizeBytes\":12,\"sha256\":\"" + "a".repeat(64) + "\"}", true));
	}

}
