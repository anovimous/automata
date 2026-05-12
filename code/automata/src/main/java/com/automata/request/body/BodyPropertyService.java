package com.automata.request.body;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.Request;
import com.automata.request.common.enums.RequestContentType;
import com.automata.response.Response;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BodyPropertyService {

	private final BodyPropertyRepository bodyRepo;

	@Transactional
	public void updateBodyProvidedRaw(Request request, String body) {

		BodyParseResult parseResult;

		if (request.getContentType() == RequestContentType.JSON)
			parseResult = BodyUtils.parseJsonBody(body);
		else if (request.getContentType() == RequestContentType.FORM)
			parseResult = BodyUtils.parseFormBody(body);
		else
			parseResult = new BodyParseResult();

		List<Long> toDeleteBodyProperties = parseResult.getBodyProperties().stream().map(BodyProperty::getId)
				.collect(Collectors.toList());

		bodyRepo.deleteAllByIdInBatch(toDeleteBodyProperties);

		bodyRepo.flush();

		List<BodyProperty> persistedBodyProperties = this.persistRequestBodyProperties(parseResult.getBodyProperties(),
				request);

		request.setBodyProperties(persistedBodyProperties);

	}

	@Transactional
	public List<BodyProperty> persistRequestBodyProperties(List<BodyProperty> bodyProperties,
			Request persistedRequest) {

		bodyProperties.forEach(property -> property.setRequest(persistedRequest));

		return bodyRepo.saveAll(bodyProperties);
	}

	@Transactional
	public List<BodyProperty> persistResponseBodyProperties(List<BodyProperty> bodyProperties,
			Response persistedResponse) {

		bodyProperties.forEach(property -> property.setResponse(persistedResponse));

		return bodyRepo.saveAll(bodyProperties);
	}

}
