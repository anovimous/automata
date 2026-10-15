package com.automata.request;

import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.RawHttpMessage;
import com.automata.common.dto.response.ListHolderResponse;
import com.automata.common.dto.response.PageHolderResponse;
import com.automata.request.common.dto.EqualizationResponse;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.dto.RequestFilter;
import com.automata.request.common.dto.RequestRawResponse;
import com.automata.request.common.dto.RequestResponse;
import com.automata.request.common.dto.RequestsEqualizationDto;
import com.automata.request.equalityset.Equalization;
import com.automata.request.equalityset.EqualizationMapper;
import com.automata.request.equalityset.RequestEqualityService;
import com.automata.response.RawResponseAdditionDto;
import com.automata.response.Response;
import com.automata.storage.BodyDownloads;
import com.automata.storage.StoredBody;
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
	public ResponseEntity<RequestRawResponse> getRequest(@PathVariable Long requestId) {

		RawHttpMessage rawRequest = requestService.getRawRequest(requestId);

		Response res = responseService.getResponseOfRequest(requestId);

		RequestRawResponse response = new RequestRawResponse(rawRequest.rawHeadBase64(), rawRequest.rawBodyBase64(),
				rawRequest.bodyStoredAsFile(), res != null ? res.getId() : null);

		return ResponseEntity.ok(response);

	}

	@GetMapping("/{requestId}/body")
	public ResponseEntity<InputStreamResource> downloadRequestBody(@PathVariable Long requestId) {

		StoredBody body = requestService.getRequestBodyFile(requestId);

		return BodyDownloads.attachment(body, "request-" + requestId);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<RequestResponse>> getRequests(@ModelAttribute RequestFilter filter,
			@PageableDefault(size = 40, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Request> requests = requestService.getRequestsFilteredAndPaged(filter, pageable);

		Page<RequestResponse> cleanedRequests = RequestMapper.toRequestResponseList(requests);

		return ResponseEntity.ok(new PageHolderResponse<>(cleanedRequests));

	}

	@GetMapping("/equal")
	public ResponseEntity<PageHolderResponse<RequestResponse>> getEqualRequests(@RequestParam Long equalitySetId,
			@PageableDefault(size = 40, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Request> requests = requestService.getEqualRequestsPaged(equalitySetId, pageable);

		Page<RequestResponse> cleanedRequests = RequestMapper.toRequestResponseList(requests);

		return ResponseEntity.ok(new PageHolderResponse<>(cleanedRequests));

	}

	@PostMapping("/raw")
	public ResponseEntity<RequestResponse> addRawRequestWithResponse(@RequestBody RawRequestAdditionDto dto) {

		Request request = requestService.addRawRequestWithResponse(dto);

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

	@GetMapping("/equalizations")
	public ResponseEntity<ListHolderResponse<EqualizationResponse>> getEqualizations(@RequestParam Long hostId) {

		List<Equalization> equalizations = requestEqualityService.getEqualizations(hostId);

		List<EqualizationResponse> equalizationResponses = equalizations.stream().map(EqualizationMapper::toResponse)
				.toList();

		return ResponseEntity.ok(new ListHolderResponse<>(equalizationResponses));

	}

	@PostMapping("/equalizations")
	public ResponseEntity<Void> createEqualizationObject(@RequestBody RequestsEqualizationDto dto) {

		requestEqualityService.createEqualizationTask(dto);

		return ResponseEntity.status(201).build();

	}

	@PatchMapping("/equalizations/{equalizationId}")
	public ResponseEntity<Void> updateEqualizationObject(@PathVariable Long equalizationId,
			@RequestBody Boolean autoEqualize) {

		requestEqualityService.updateEqualization(equalizationId, autoEqualize);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/equalizations/run")
	public ResponseEntity<Void> equalizeRequests(@RequestParam Long equalizationId) {

		requestEqualityService.equalizeRequestsIntoEqualityGroups(equalizationId);

		return ResponseEntity.status(204).build();

	}

	@DeleteMapping("{requestId}")
	public ResponseEntity<Void> deleteRequest(@PathVariable Long requestId) {

		requestService.deleteRequest(requestId);

		return ResponseEntity.status(204).build();
	}

}
