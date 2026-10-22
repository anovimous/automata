package com.automata.job.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.automata.job.domain.valueobject.JobResultFile;
import com.automata.job.service.HttpJobCreationService;
import com.automata.job.service.HttpJobQueryService;
import com.automata.job.service.HttpJobStateService;
import com.automata.routine.common.enums.ResultContentType;

import io.awspring.cloud.s3.S3Resource;

class HttpJobControllerDownloadResultTest {

	private final HttpJobQueryService queryService = mock(HttpJobQueryService.class);

	private final HttpJobController controller = new HttpJobController(mock(HttpJobCreationService.class),
			queryService, mock(HttpJobStateService.class));

	private void result(ResultContentType type) throws Exception {
		S3Resource resource = mock(S3Resource.class);
		when(resource.contentLength()).thenReturn(12L);
		when(queryService.getJobResult(7L)).thenReturn(new JobResultFile(resource, type));
	}

	@Test
	void inlineServesTheRoutineContentTypeWithoutADownloadHeader() throws Exception {
		result(ResultContentType.JSON);

		ResponseEntity<StreamingResponseBody> response = controller.downloadJobResult(7L, true);

		assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
		assertNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
		assertEquals(12L, response.getHeaders().getContentLength());
	}

	@Test
	void inlineServesPlainTextForATextRoutine() throws Exception {
		result(ResultContentType.TEXT);

		assertEquals(MediaType.TEXT_PLAIN, controller.downloadJobResult(7L, true).getHeaders().getContentType());
	}

	@Test
	void withoutInlineTheResultStaysADownload() throws Exception {
		result(ResultContentType.JSON);

		ResponseEntity<StreamingResponseBody> response = controller.downloadJobResult(7L, false);

		assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
		assertEquals("attachment; filename=\"7.result\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
	}

}
