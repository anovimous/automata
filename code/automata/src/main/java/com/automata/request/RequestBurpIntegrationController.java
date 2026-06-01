package com.automata.request;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.request.common.dto.BurpRequestAdditionDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/external/requests")
@RequiredArgsConstructor
public class RequestBurpIntegrationController {

	private final BurpAdapterService adapter;

	@PostMapping("")
	public ResponseEntity<Void> addRawRequest(@RequestBody BurpRequestAdditionDto dto) {

		adapter.addRawRequest(dto.requestBase64(), dto.host(), dto.base64Response());

		return ResponseEntity.status(201).build();
	}

}
