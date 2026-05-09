package com.automata.export;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

	private final BodyPropertyExportService bodyPropertyExportService;

	@GetMapping("/body-properties")
	public ResponseEntity<StreamingResponseBody> downloadDistinctBodyPropertyNames(
			@RequestParam(required = false) Long hostId, @RequestParam(required = false) Long programId) {

		BodyPropertyExportResult result = bodyPropertyExportService.export(hostId, programId);

		StreamingResponseBody body = outputStream -> {
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
			for (String name : result.names())
				writer.println(name);
			writer.flush();
		};

		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
				.body(body);

	}

}
