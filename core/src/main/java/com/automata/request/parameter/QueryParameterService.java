package com.automata.request.parameter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.Request;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueryParameterService {

	private final QueryParameterRepository queryParamRepo;

	@Transactional
	public void updateQueryParametersProvidedQueryString(Request request, String queryString) {

		QueryStringParseResult parseResult = QueryParameterUtils.parseQueryString(queryString);

		List<Long> toDeleteQueryParametersIds = request.getParameters().stream().map(QueryParameter::getId)
				.collect(Collectors.toList());

		queryParamRepo.deleteAllByIdInBatch(toDeleteQueryParametersIds);

		queryParamRepo.flush();

		List<QueryParameter> persistedQueryParameters = this
				.persistRequestQueryParameters(parseResult.getQueryParameters(), request);

		request.setParameters(persistedQueryParameters);
	}

	@Transactional
	public List<QueryParameter> persistRequestQueryParameters(List<QueryParameter> queryParameters,
			Request persistedRequest) {

		queryParameters.forEach(param -> param.setRequest(persistedRequest));

		return queryParamRepo.saveAll(queryParameters);

	}

}
