package com.automata.export;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.automata.testsupport.Mvc;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

	@Mock
	BodyPropertyExportService exportService;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new ExportController(exportService)).build();
	}

	@Test
	void theNamesAreStreamedOnePerLineAsAnAttachment() throws Exception {
		when(exportService.export(1L, null)).thenReturn(new BodyPropertyExportResult(List.of("user", "name"), "names.txt"));

		MvcResult started = mvc.perform(get("/api/export/body-properties").param("hostId", "1"))
				.andExpect(request().asyncStarted()).andReturn();

		mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"names.txt\""))
				.andExpect(header().string("Content-Type", "text/plain")).andExpect(content().string("user\nname\n"));
	}

	@Test
	void noNamesIsAnEmptyFile() throws Exception {
		when(exportService.export(null, 2L)).thenReturn(new BodyPropertyExportResult(List.of(), "names.txt"));

		MvcResult started = mvc.perform(get("/api/export/body-properties").param("programId", "2"))
				.andExpect(request().asyncStarted()).andReturn();

		mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().string(""));
	}

	@Test
	void missingBothParametersAnswers400() throws Exception {
		when(exportService.export(null, null)).thenThrow(new IllegalArgumentException("Either hostId or programId must be provided"));

		mvc.perform(get("/api/export/body-properties")).andExpect(status().isBadRequest());
	}
}
