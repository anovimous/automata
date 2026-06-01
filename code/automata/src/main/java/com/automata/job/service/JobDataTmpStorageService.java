package com.automata.job.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.common.dto.HostInternalDto;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.job.domain.valueobject.HostData;
import com.automata.job.domain.valueobject.RequestData;
import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.job.infra.JobDataJsonLinesWriter;
import com.automata.job.repository.NarrowJobTargetRequestRepository;
import com.automata.job.repository.WideJobTargetHostRepository;
import com.automata.request.RequestUtils;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.path.PathVariableRepository;

@Service
public class JobDataTmpStorageService {

	public JobDataTmpStorageService(@Value("${com.automata.files.tmp.location}") String tmpDir,
			NarrowJobTargetRequestRepository narrowJobTargetRequestRepo,
			WideJobTargetHostRepository wideJobTargetHostRepo, PathVariableRepository pathVariableRepo,
			QueryParameterRepository queryParameterRepo, BodyPropertyRepository bodyPropertyRepo) {
		this.TMP_DIR = tmpDir;
		this.narrowJobTargetRequestRepo = narrowJobTargetRequestRepo;
		this.wideJobTargetHostRepo = wideJobTargetHostRepo;
		this.pathVariableRepo = pathVariableRepo;
		this.queryParameterRepo = queryParameterRepo;
		this.bodyPropertyRepo = bodyPropertyRepo;
	}

	@Value("${com.automata.files.tmp.location}")
	private final String TMP_DIR;

	private final NarrowJobTargetRequestRepository narrowJobTargetRequestRepo;

	private final WideJobTargetHostRepository wideJobTargetHostRepo;

	private final PathVariableRepository pathVariableRepo;

	private final QueryParameterRepository queryParameterRepo;

	private final BodyPropertyRepository bodyPropertyRepo;

	@Transactional(readOnly = true)
	public String tempStoreNarrowJobTargetData(NarrowHttpJob fullyConfiguredJob) {

		NarrowTargetConfig targetConfig = fullyConfiguredJob.getTargetConfig();

		JobDataJsonLinesWriter writer;

		String fileLocation = TMP_DIR + "/" + fullyConfiguredJob.getId();

		try {
			writer = new JobDataJsonLinesWriter(fileLocation);
		} catch (IOException e) {
			throw new RuntimeException("Json Lines Writer could not be created");
		}

		Host host = fullyConfiguredJob.getHost();

		if (targetConfig.getTargetHost() != null) {

			HostData hostData = new HostData(host.getId(), host.getHost(), host.getScope());

			try {
				writer.writeHostData(hostData);
				writer.flushAndClose();
			} catch (IOException e) {
				throw new RuntimeException("Error writing RequestData object to JSONL file");
			}

		} else {

			// NOTE: can be optimized later by only paginating requests that have a number
			// of properties > 0

			int page = 0;

			Page<RequestInternalDto> requestDtosPage;

			do {
				requestDtosPage = narrowJobTargetRequestRepo.getRequestDtosByJob(fullyConfiguredJob,

						PageRequest.of(page, 50));

				List<Long> requestIds = requestDtosPage.map(RequestInternalDto::getRequestId).toList();

				Map<Long, List<PathVariableInternalDto>> pathVariablesByRequest = pathVariableRepo
						.getPathVariableDtosOfRequests(requestIds).stream()
						.collect(Collectors.groupingBy(PathVariableInternalDto::requestId));

				Map<Long, List<QueryParameterInternalDto>> queryParametersByRequest = queryParameterRepo
						.getQueryParameterDtosByRequestsIds(requestIds).stream()
						.collect(Collectors.groupingBy(QueryParameterInternalDto::requestId));

				Map<Long, List<BodyPropertyInternalDto>> bodyPropertiesByRequest = bodyPropertyRepo
						.getBodyPropertyDtosByRequestsIds(requestIds).stream()
						.collect(Collectors.groupingBy(BodyPropertyInternalDto::requestId));

				// process them

				List<RequestInternalDto> fullyPopulatedRequestDtos = requestDtosPage.map((dto) -> {

					List<PathVariableInternalDto> pathVariables = pathVariablesByRequest.get(dto.getRequestId()) != null
							? pathVariablesByRequest.get(dto.getRequestId())
							: new ArrayList<PathVariableInternalDto>();

					dto.setPathVariableDtos(pathVariables);

					List<QueryParameterInternalDto> queryParams = queryParametersByRequest
							.get(dto.getRequestId()) != null ? queryParametersByRequest.get(dto.getRequestId())
									: new ArrayList<QueryParameterInternalDto>();

					dto.setQueryParameterDtos(queryParams);

					List<BodyPropertyInternalDto> bodyProperties = bodyPropertiesByRequest
							.get(dto.getRequestId()) != null ? bodyPropertiesByRequest.get(dto.getRequestId())
									: new ArrayList<BodyPropertyInternalDto>();

					dto.setBodyPropertyDtos(bodyProperties);

					return dto;

				}).toList();

				fullyPopulatedRequestDtos.stream().forEach((dto) -> {

					ClassicHttpRequest apacheRequest = RequestUtils.composeApacheCoreRequest(dto, host.getHost());

					// OPT: apply preemptive match and replace rules here

					String rawRequest = RequestUtils.composeRawRequest(apacheRequest);

					RequestData singleRequestData = new RequestData(dto.getRequestId(),
							Base64.getEncoder().encodeToString(rawRequest.getBytes(StandardCharsets.UTF_8)));

					try {
						writer.writeRequestData(singleRequestData);
					} catch (IOException e) {
						throw new RuntimeException("Error writing RequestData object to JSONL file");
					}

				});

			} while (requestDtosPage.hasNext());

			try {
				writer.flushAndClose();
			} catch (IOException e) {
				throw new RuntimeException("Error writing RequestData object to JSONL file");
			}

		}

		return fileLocation;

	}

	@Transactional(readOnly = true)
	public String tempStoreWideJobTargetData(WideHttpJob fullyConfiguredJob) {

		JobDataJsonLinesWriter writer;

		String fileLocation = TMP_DIR + "/" + fullyConfiguredJob.getId();

		try {
			writer = new JobDataJsonLinesWriter(fileLocation);
		} catch (IOException e) {
			throw new RuntimeException("Json Lines Writer could not be created");
		}

		try (Stream<HostInternalDto> hostDtosStream = wideJobTargetHostRepo.getHostDtosByJob(fullyConfiguredJob)) {

			hostDtosStream.forEach((dto) -> {
				try {
					writer.writeHostData(new HostData(dto.hostId(), dto.host(), dto.scope()));
				} catch (IOException e) {
					throw new RuntimeException("Exception occured while writing HostData to tmp JSON file");
				}
			});

		}

		return fileLocation;

	}

}
