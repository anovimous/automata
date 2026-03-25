package com.automata.job;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.common.dto.HostInternalDto;
import com.automata.job.common.dto.RequestInternalDto;
import com.automata.job.targetconfig.NarrowTargetConfig;
import com.automata.request.RequestUtils;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.path.PathVariableRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobConfigFileService {

	private final String TMP_DIR = "/var/tmp/";

	private final NarrowJobTargetRequestRepository narrowJobTargetRequestRepo;

	private final WideJobTargetHostRepository wideJobTargetHostRepo;

	private final PathVariableRepository pathVariableRepo;

	private final QueryParameterRepository queryParameterRepo;

	private final BodyPropertyRepository bodyPropertyRepo;

	private final S3Service s3Service;

	@Async("fileConstructionExecutor")
	public void createNarrowConfigFile(NarrowHttpJob fullyConfiguredJob) {

		JobDetailsFileContainer container = this.createJobDetailsContainer(fullyConfiguredJob);

		s3Service.storeJobDetailsObject(container);

		String dataTempFileLocation = this.tempStoreNarrowJobTargetData(fullyConfiguredJob);

		s3Service.storeJobDataFile(dataTempFileLocation, fullyConfiguredJob.getId());

	}

	@Async("fileConstructionExecutor")
	public void createWideConfigFile(WideHttpJob fullyConfiguredJob) {

		JobDetailsFileContainer container = this.createJobDetailsContainer(fullyConfiguredJob);

		s3Service.storeJobDetailsObject(container);

		String dataTempFileLocation = this.tempStoreWideJobTargetData(fullyConfiguredJob);

		s3Service.storeJobDataFile(dataTempFileLocation, fullyConfiguredJob.getId());

	}

	private JobDetailsFileContainer createJobDetailsContainer(HttpJob job) {

		return JobDetailsFileContainer.builder().jobId(job.getId()).creationDate(job.getCreationDate())
				.verbosity(job.getGenericDetails().getVerbosity())
				.customConfig(job.getGenericDetails().getCustomConfig())
				.genericConfig(job.getGenericDetails().getGenericConfig()).build();

	}

	private String tempStoreNarrowJobTargetData(NarrowHttpJob fullyConfiguredJob) {

		NarrowTargetConfig targetConfig = fullyConfiguredJob.getTargetConfig();

		JobDataJsonLinesWriter writer;

		String fileLocation = TMP_DIR + fullyConfiguredJob.getId();

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

					dto.setPathVariableDtos(pathVariablesByRequest.get(dto.getRequestId()));

					dto.setQueryParameterDtos(queryParametersByRequest.get(dto.getRequestId()));

					dto.setBodyPropertyDtos(bodyPropertiesByRequest.get(dto.getRequestId()));

					return dto;

				}).toList();

				fullyPopulatedRequestDtos.stream().forEach((dto) -> {

					ClassicHttpRequest apacheRequest = RequestUtils.composeApacheCoreRequest(dto, host.getHost());

					// OPT: apply preemptive match and replace rules here

					RequestData singleRequestData = new RequestData(dto.getRequestId(),
							RequestUtils.composeRawRequest(apacheRequest));

					try {
						writer.writeRequestData(singleRequestData);
					} catch (IOException e) {
						throw new RuntimeException("Error writing RequestData object to JSONL file");
					}

				});

			} while (requestDtosPage.hasNext());

		}

		return fileLocation;

	}

	@Transactional
	private String tempStoreWideJobTargetData(WideHttpJob fullyConfiguredJob) {

		JobDataJsonLinesWriter writer;

		String fileLocation = TMP_DIR + fullyConfiguredJob.getId();

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
