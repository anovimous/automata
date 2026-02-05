package com.automata.request.path;

import java.util.stream.Collectors;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.Request;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PathService {

	private final PathVariableRepository pathVariableRepo;

	@Transactional
	public void updatePath(Request request, String path) {

		PathUtils.validatePath(path).ifNotValidThrow(() -> new IllegalArgumentException("Path format invalid"));

		PathParsingResult parseResult = PathUtils.parsePath(path);

		List<Long> toDeletePathVariables = request.getPathVariables().stream().map(PathVariable::getId)
				.collect(Collectors.toList());

		pathVariableRepo.deleteAllByIdInBatch(toDeletePathVariables);

		request.setComputatedPath(parseResult.getComputatedPath());

		request.setExtension(parseResult.getExtension());

		List<PathVariable> toSetPathVariables = parseResult.getPathVariables();

		pathVariableRepo.saveAll(toSetPathVariables);

		request.setPathVariables(toSetPathVariables);

	}

	public List<PathVariable> persistPathVariables(List<PathVariable> pathVariables) {

		return pathVariableRepo.saveAll(pathVariables);

	}

}
