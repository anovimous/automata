package com.automata.request.body;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.Request;
import com.automata.request.common.enums.ContentType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BodyPropertyService {

	private final BodyPropertyRepository bodyRepo;

	@Transactional
	public void updateBodyProvidedRaw(Request request, String body) {

		BodyParseResult parseResult;

		if (request.getContentType() == ContentType.JSON)
			parseResult = BodyUtils.parseJsonBody(body);
		else if (request.getContentType() == ContentType.FORM)
			parseResult = BodyUtils.parseFormBody(body);
		else
			parseResult = new BodyParseResult();

		List<Long> toDeleteBodyProperties = parseResult.getBodyProperties().stream().map(BodyProperty::getId)
				.collect(Collectors.toList());

		bodyRepo.deleteAllByIdInBatch(toDeleteBodyProperties);

		List<BodyProperty> toSetBodyProperties = parseResult.getBodyProperties();

		bodyRepo.saveAll(toSetBodyProperties);

		request.setBodyProperties(parseResult.getBodyProperties());

	}

	public List<BodyProperty> persistBodyProperties(List<BodyProperty> bodyProperties) {

		return bodyRepo.saveAll(bodyProperties);
	}

}
