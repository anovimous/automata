package com.automata.job.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.common.dto.HostInternalDto;
import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.job.domain.model.enums.TargetType;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.HostData;
import com.automata.job.domain.valueobject.JobDetailsFileContainer;
import com.automata.job.domain.valueobject.RequestData;
import com.automata.job.domain.valueobject.RequestInternalDto;
import com.automata.job.infra.JobDataJsonLinesWriter;
import com.automata.job.infra.S3Service;
import com.automata.job.repository.HttpJobRepository;
import com.automata.job.repository.NarrowJobTargetRequestRepository;
import com.automata.job.repository.WideJobTargetHostRepository;
import com.automata.request.RequestUtils;
import com.automata.request.body.BodyPropertyRepository;
import com.automata.request.common.dto.BodyPropertyInternalDto;
import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.dto.QueryParameterInternalDto;
import com.automata.request.parameter.QueryParameterRepository;
import com.automata.request.path.PathVariableRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.authentication.Authentication;
import com.automata.tenant.authentication.AuthenticationRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JobConfigFileService {

	public JobConfigFileService(@Value("${com.automata.files.tmp.location}") String tmpDir,
			NarrowJobTargetRequestRepository narrowJobTargetRequestRepo,
			WideJobTargetHostRepository wideJobTargetHostRepo, PathVariableRepository pathVariableRepo,
			QueryParameterRepository queryParameterRepo, BodyPropertyRepository bodyPropertyRepo,
			HttpJobRepository httpJobRepo, AuthenticationRepository authRepo, S3Service s3Service,
			RoutineRepository routineRepo) {
		this.TMP_DIR = tmpDir;
		this.narrowJobTargetRequestRepo = narrowJobTargetRequestRepo;
		this.wideJobTargetHostRepo = wideJobTargetHostRepo;
		this.pathVariableRepo = pathVariableRepo;
		this.queryParameterRepo = queryParameterRepo;
		this.bodyPropertyRepo = bodyPropertyRepo;
		this.httpJobRepo = httpJobRepo;
		this.authRepo = authRepo;
		this.routineRepo = routineRepo;
		this.s3Service = s3Service;
	}

	@Value("${com.automata.files.tmp.location}")
	private final String TMP_DIR;

	private final NarrowJobTargetRequestRepository narrowJobTargetRequestRepo;

	private final WideJobTargetHostRepository wideJobTargetHostRepo;

	private final PathVariableRepository pathVariableRepo;

	private final QueryParameterRepository queryParameterRepo;

	private final BodyPropertyRepository bodyPropertyRepo;

	private final HttpJobRepository httpJobRepo;

	private final AuthenticationRepository authRepo;

	private final RoutineRepository routineRepo;

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

	@Transactional(readOnly = true)
	public JobDetailsFileContainer createJobDetailsContainer(HttpJob job) {

		TargetType targetType = switch (job.getGenericDetails().getTargetSelector().getSelectorType()) {
		case SelectorType.SINGLE_HOST, SelectorType.MULTIPLE_HOSTS -> TargetType.HOST;
		default -> TargetType.REQUEST;

		};
		Routine routine = routineRepo.findById(job.getRoutine().getId())
				.orElseThrow(() -> new EntityNotFoundException("Routine not found"));

		Authentication auth = new Authentication();

		if (job.getGenericDetails().getHttpJobScope() == HttpJobScope.NARROW) {
			NarrowHttpJob castedJob = (NarrowHttpJob) job;
			auth = authRepo.findByTenant(castedJob.getTenant());
		}

		Set<String> wordlistsPaths = httpJobRepo.findWordlistPathsByJobId(job.getId());

		return JobDetailsFileContainer.builder().jobId(job.getId()).rate(job.getGenericDetails().getRate())
				.verbosity(job.getGenericDetails().getVerbosity()).targetType(targetType).routineKey(routine.getKey())
				.auth(auth.getAuthData()).wordlistsPaths(wordlistsPaths)
				.customConfig(job.getGenericDetails().getCustomConfig())
				.genericConfig(job.getGenericDetails().getGenericConfig()).build();

	}

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
