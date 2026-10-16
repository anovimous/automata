package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.automata.host.Host;
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.job.repository.NarrowJobTargetRequestRepository;
import com.automata.job.repository.WideJobTargetHostRepository;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.PropertyValueType;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Source;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.path.PathVariableRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobDataTmpStorageServiceTest {

	@TempDir
	Path tmpDir;

	@Mock
	NarrowJobTargetRequestRepository narrowRepo;
	@Mock
	WideJobTargetHostRepository wideRepo;
	@Mock
	PathVariableRepository pathRepo;
	@Mock
	QueryParameterRepository queryRepo;
	@Mock
	BodyPropertyRepository bodyRepo;

	JobDataTmpStorageService service;

	NarrowHttpJob job;

	@BeforeEach
	void setUp() {
		service = new JobDataTmpStorageService(tmpDir.toString(), narrowRepo, wideRepo, pathRepo, queryRepo,
				bodyRepo);

		Host host = new Host();
		host.setId(1L);
		host.setHost("example.com");

		job = new NarrowHttpJob();
		job.setId(77L);
		job.setHost(host);
		job.setTargetConfig(NarrowTargetConfig.builder().build());

		when(pathRepo.getPathVariableDtosOfRequests(any())).thenReturn(List.of());
		when(queryRepo.getQueryParameterDtosByRequestsIds(any())).thenReturn(List.of());
	}

	private static RequestInternalDto dto(Long id, RequestContentType contentType, long size) {
		return new RequestInternalDto(id, Method.POST, "/a", null, "HTTP/1.1", 1, contentType, size, Source.BURP);
	}

	@Test
	void jsonRequestIsRebuiltWithItsBody() throws Exception {
		when(narrowRepo.getRequestDtosByJob(any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(dto(10L, RequestContentType.JSON, 9))));
		when(bodyRepo.getBodyPropertyDtosByRequestsIds(any())).thenReturn(
				List.of(new BodyPropertyInternalDto("k", "v", PropertyValueType.STRING, 10L, null)));

		String location = service.tempStoreNarrowJobTargetData(job);

		String line = Files.readAllLines(Path.of(location)).get(0);
		assertTrue(line.contains("\"requestId\":10"));
		String base64 = line.replaceAll(".*\"base64Request\":\"([^\"]+)\".*", "$1");
		String raw = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
		assertTrue(raw.startsWith("POST /a HTTP/1.1"));
		assertTrue(raw.endsWith("{\"k\":\"v\"}"));
	}

	@Test
	void bodylessRequestIsRebuiltWithoutBody() throws Exception {
		when(narrowRepo.getRequestDtosByJob(any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(dto(11L, null, 0))));
		when(bodyRepo.getBodyPropertyDtosByRequestsIds(any())).thenReturn(List.of());

		String location = service.tempStoreNarrowJobTargetData(job);

		List<String> lines = Files.readAllLines(Path.of(location));
		assertEquals(1, lines.size());
		String base64 = lines.get(0).replaceAll(".*\"base64Request\":\"([^\"]+)\".*", "$1");
		String raw = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
		assertTrue(raw.endsWith("\r\n\r\n"));
	}

	@Test
	void ineligibleRequestIsRejected() {
		when(narrowRepo.getRequestDtosByJob(any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(dto(12L, RequestContentType.MULTIPART, 50))));
		when(bodyRepo.getBodyPropertyDtosByRequestsIds(any())).thenReturn(List.of());

		assertThrows(IneligibleJobTargetException.class, () -> service.tempStoreNarrowJobTargetData(job));
	}

}
