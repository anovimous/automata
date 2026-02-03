package com.automata.request.body;

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
	public void updateBodyProvidedRaw(Request request, ContentType contentType, String body) {

		BodyParseResult parseResult;

		if (contentType == ContentType.JSON)
			parseResult = BodyUtils.parseJsonBody(body);
		else if (contentType == ContentType.FORM)
			parseResult = BodyUtils.parseFormBody(body);
		else
			parseResult = new BodyParseResult();

		request.setBodyProperties(parseResult.getBodyProperties());

	}

}
