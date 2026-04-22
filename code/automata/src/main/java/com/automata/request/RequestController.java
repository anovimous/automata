package com.automata.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.host.common.dto.RequestFilter;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestAdditionDto;
import com.automata.request.common.dto.RequestPatchDto;
import com.automata.request.common.dto.RequestResponse;
import com.automata.request.common.dto.RequestsEqualizationDto;
import com.automata.response.RawResponseAdditionDto;
import com.automata.response.ResponseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

	private final RequestService requestService;

	private final RequestEqualityService requestEqualityService;

	private final ResponseService responseService;

	@GetMapping("/{requestId}")
	public ResponseEntity<RequestResponse> getRequest(@PathVariable Long requestId) {

		Request request = requestService.getRequestById(requestId);

		RequestResponse response = RequestMapper.toRequestResponse(request);

		return ResponseEntity.ok(response);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<RequestResponse>> getRequests(
			@RequestParam(required = false) RequestFilter filter, @PageableDefault(size = 40, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Request> requests = requestService.getRequestsFilteredAndPaged(filter, pageable);

		Page<RequestResponse> cleanedRequests = RequestMapper.toRequestResponseList(requests);

		return ResponseEntity.ok(new PageHolderResponse<>(cleanedRequests));

	}

	@PostMapping("/equalize")
	public ResponseEntity<Void> equalizeRequests(RequestsEqualizationDto dto) {

		requestEqualityService.equalizeRequestsIntoEqualityGroups(dto);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("")
	public ResponseEntity<RequestResponse> addRequestViaComponents(@RequestBody RequestAdditionDto dto) {

		return ResponseEntity.status(503).build();

//		Request request = requestService.addRequestViaComponents(dto);
//
//		RequestResponse response = RequestMapper.toRequestResponse(request);
//
//		return ResponseEntity.status(201).body(response);

	}

	@PostMapping("/raw")
	public ResponseEntity<RequestResponse> addRawRequestWithResponse(@RequestBody RawRequestAdditionDto dto) {

		Request request = requestService.addRawRequest(dto);

		responseService.addResponseToRequest(dto.responseBase64(), request);

		RequestResponse requestResponse = RequestMapper.toRequestResponse(request);

		return ResponseEntity.status(201).body(requestResponse);
	}

	@PostMapping("/{requestId}/response")
	public ResponseEntity<RequestResponse> associateExistingRequestToNewRawResponse(@PathVariable Long requestId,
			@RequestBody RawResponseAdditionDto dto) {

		Request request = requestService.getRequestById(requestId);

		responseService.addResponseToRequest(dto.responseBase64(), request);

		RequestResponse requestResponse = RequestMapper.toRequestResponse(request);

		return ResponseEntity.ok(requestResponse);

	}

	@PatchMapping("/{requestId}")
	public ResponseEntity<RequestResponse> patchRequestProperties(@PathVariable Long requestId,
			@RequestBody RequestPatchDto dto) {

		Request request = requestService.patchRequest(requestId, dto);

		RequestResponse requestResponse = RequestMapper.toRequestResponse(request);

		return ResponseEntity.status(204).body(requestResponse);

	}

	@DeleteMapping("{requestId}")
	public ResponseEntity<Void> deleteRequest(@PathVariable Long requestId) {

		requestService.deleteRequest(requestId);

		return ResponseEntity.status(204).build();
	}

}
