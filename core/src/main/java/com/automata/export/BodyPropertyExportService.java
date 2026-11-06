package com.automata.export;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.body.BodyPropertyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BodyPropertyExportService {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);

	private final BodyPropertyRepository bodyPropertyRepo;

	@Transactional(readOnly = true)
	public BodyPropertyExportResult exportByHostId(Long hostId) {

		List<String> requestPaths = bodyPropertyRepo.findDistinctFullPathsByRequestHostId(hostId);
		List<String> responsePaths = bodyPropertyRepo.findDistinctFullPathsByResponseHostId(hostId);

		List<String> names = mergeAndExtractNames(requestPaths, responsePaths);

		String filename = new StringBuilder().append("body-properties-host-").append(hostId)
				.append("-").append(timestamp()).append(".txt").toString();

		return new BodyPropertyExportResult(names, filename);

	}

	@Transactional(readOnly = true)
	public BodyPropertyExportResult exportByProgramId(Long programId) {

		List<String> requestPaths = bodyPropertyRepo.findDistinctFullPathsByRequestProgramId(programId);
		List<String> responsePaths = bodyPropertyRepo.findDistinctFullPathsByResponseProgramId(programId);

		List<String> names = mergeAndExtractNames(requestPaths, responsePaths);

		String filename = new StringBuilder().append("body-properties-program-").append(programId)
				.append("-").append(timestamp()).append(".txt").toString();

		return new BodyPropertyExportResult(names, filename);

	}

	public BodyPropertyExportResult export(Long hostId, Long programId) {

		if (hostId == null && programId == null)
			throw new IllegalArgumentException("Either hostId or programId must be provided");

		if (hostId != null && programId != null)
			throw new IllegalArgumentException("Only one of hostId or programId can be provided, not both");

		return hostId != null ? exportByHostId(hostId) : exportByProgramId(programId);

	}

	private List<String> mergeAndExtractNames(List<String> requestPaths, List<String> responsePaths) {

		return Stream.concat(requestPaths.stream(), responsePaths.stream())
				.flatMap(path -> BodyPropertyPathUtils.extractNames(path).stream()).distinct().collect(Collectors.toList());

	}

	// no colon: it is not allowed in a file name on every system
	private static String timestamp() {
		return TIMESTAMP.format(Instant.now());
	}

}
