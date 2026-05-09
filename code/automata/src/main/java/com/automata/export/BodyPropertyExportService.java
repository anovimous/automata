package com.automata.export;

import java.time.Instant;
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

	private final BodyPropertyRepository bodyPropertyRepo;

	@Transactional(readOnly = true)
	public BodyPropertyExportResult exportByHostId(Long hostId) {

		List<String> requestPaths = bodyPropertyRepo.findDistinctFullPathsByRequestHostId(hostId);
		List<String> responsePaths = bodyPropertyRepo.findDistinctFullPathsByResponseHostId(hostId);

		List<String> names = mergeAndExtractNames(requestPaths, responsePaths);

		String filename = new StringBuilder().append("body-properties-host-").append(hostId)
				.append(Instant.now().toString()).append(".txt").toString();

		return new BodyPropertyExportResult(names, filename);

	}

	@Transactional(readOnly = true)
	public BodyPropertyExportResult exportByProgramId(Long programId) {

		List<String> requestPaths = bodyPropertyRepo.findDistinctFullPathsByRequestProgramId(programId);
		List<String> responsePaths = bodyPropertyRepo.findDistinctFullPathsByResponseProgramId(programId);

		List<String> names = mergeAndExtractNames(requestPaths, responsePaths);

		String filename = new StringBuilder().append("body-properties-program-").append(programId)
				.append(Instant.now().toString()).append(".txt").toString();

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
				.flatMap(path -> extractAllSegments(path).stream()).distinct().collect(Collectors.toList());

	}

	/**
	 * Extracts all property names from a flattened JSON path or a form field name.
	 *
	 * Examples: "user.address.city" -> ["user", "address", "city"] "items[0]" ->
	 * ["items"] "items[1].name" -> ["items", "name"] "username" -> ["username"]
	 */
	private List<String> extractAllSegments(String fullPath) {

		return Stream.of(fullPath.split("\\.")).map(segment -> {
			int bracketIndex = segment.indexOf('[');
			return bracketIndex >= 0 ? segment.substring(0, bracketIndex) : segment;
		}).filter(segment -> !segment.isBlank()).distinct().collect(Collectors.toList());

	}

}