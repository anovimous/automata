package com.automata.agentcontext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.agentcontext.common.dto.AiRequestAdditionDto;
import com.automata.request.Request;
import com.automata.request.RequestMapper;
import com.automata.request.common.dto.RequestSummaryResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/external/ai/requests")
@RequiredArgsConstructor
public class AiRequestIngestionController {

	private final AiRequestIngestionService ingestionService;

	// No Location header: the agent cannot read the request back unless it is in one
	// of its contexts
	@PostMapping("")
	public ResponseEntity<RequestSummaryResponse> addRawRequest(@RequestBody AiRequestAdditionDto dto) {

		Request request = ingestionService.addRawRequest(dto);

		return ResponseEntity.status(201).body(RequestMapper.toRequestSummaryResponse(request));

	}

}
