package com.automata.response;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.RawHttpMessage;
import com.automata.common.dto.response.PageHolderResponse;
import com.automata.storage.BodyDownloads;
import com.automata.storage.StoredBody;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/responses")
public class ResponseController {

	private final ResponseService responseService;

	@GetMapping("/{responseId}/metadata")
	public ResponseEntity<ResponseMetadataDto> getResponseMetadata(@PathVariable Long responseId) {

		return ResponseEntity.ok(responseService.getResponseMetadata(responseId));

	}

	@GetMapping("/{responseId}/raw")
	public ResponseEntity<RawResponseDto> getRawResponse(@PathVariable Long responseId) {

		RawHttpMessage rawResponse = responseService.getRawResponse(responseId);

		RawResponseDto responseDto = new RawResponseDto(rawResponse.rawHeadBase64(), rawResponse.rawBodyBase64(),
				rawResponse.bodyStoredAsFile());

		return ResponseEntity.ok(responseDto);

	}

	@GetMapping("/{responseId}/body/stream")
	public ResponseEntity<InputStreamResource> streamResponseBody(@PathVariable Long responseId) {

		StoredBody body = responseService.getResponseBody(responseId);

		return BodyDownloads.attachment(body, "response-" + responseId);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<ResponseSummaryDto>> getResponses(@ModelAttribute ResponseFilter filter,
			@PageableDefault(size = 40, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Response> responses = responseService.getResponsesFilteredAndPaged(filter, pageable);

		Page<ResponseSummaryDto> cleanedResponses = ResponseMapper.toSummaryDtoPage(responses);

		return ResponseEntity.ok(new PageHolderResponse<>(cleanedResponses));

	}

	@DeleteMapping("{responseId}")
	public ResponseEntity<Void> deleteResponse(@PathVariable Long responseId) {

		responseService.deleteResponse(responseId);

		return ResponseEntity.status(204).build();
	}

}
