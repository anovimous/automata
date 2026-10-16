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
import com.automata.job.domain.exception.IneligibleJobTargetException;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.utils.HttpJobUtils;
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

	private static final int REQUESTS_PAGE_SIZE = 50;

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

		String fileLocation = TMP_DIR + "/" + fullyConfiguredJob.getId();

		Host host = fullyConfiguredJob.getHost();

		try (JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(fileLocation)) {

			if (targetConfig.getTargetHost() != null)
				writer.writeHostData(new HostData(host.getId(), host.getHost(), host.getScope()));
			else
				writeTargetRequestsData(writer, fullyConfiguredJob, host);

		} catch (IOException e) {
			throw new RuntimeException("Error writing the job target data to the JSONL file", e);
		}

		return fileLocation;

	}

	private void writeTargetRequestsData(JobDataJsonLinesWriter writer, NarrowHttpJob job, Host host)
			throws IOException {

		// NOTE: can be optimized later by only paginating requests that have a number
		// of properties > 0

		int page = 0;

		Page<RequestInternalDto> requestDtosPage;

		do {
			requestDtosPage = narrowJobTargetRequestRepo.getRequestDtosByJob(job,
					PageRequest.of(page, REQUESTS_PAGE_SIZE));

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

			for (RequestInternalDto dto : requestDtosPage) {

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

				if (!HttpJobUtils.isJobEligible(dto.getContentType(), dto.getBodySizeBytes()))
					throw IneligibleJobTargetException.forRequest(dto.getRequestId(), dto.getContentType());

				ClassicHttpRequest apacheRequest = RequestUtils.composeApacheCoreRequest(dto, host.getHost());

				// OPT: apply preemptive match and replace rules here

				String rawRequest = RequestUtils.composeRawRequest(apacheRequest, dto.getVersion());

				writer.writeRequestData(new RequestData(dto.getRequestId(),
						Base64.getEncoder().encodeToString(rawRequest.getBytes(StandardCharsets.UTF_8))));

			}

			page++;

		} while (requestDtosPage.hasNext());

	}

	@Transactional(readOnly = true)
	public String tempStoreWideJobTargetData(WideHttpJob fullyConfiguredJob) {

		String fileLocation = TMP_DIR + "/" + fullyConfiguredJob.getId();

		try (JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(fileLocation);
				Stream<HostInternalDto> hostDtosStream = wideJobTargetHostRepo.getHostDtosByJob(fullyConfiguredJob)) {

			hostDtosStream.forEach((dto) -> {
				try {
					writer.writeHostData(new HostData(dto.hostId(), dto.host(), dto.scope()));
				} catch (IOException e) {
					throw new RuntimeException("Exception occured while writing HostData to tmp JSON file", e);
				}
			});

		} catch (IOException e) {
			throw new RuntimeException("Error writing the job target data to the JSONL file", e);
		}

		return fileLocation;

	}

}
