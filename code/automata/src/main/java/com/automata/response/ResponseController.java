package com.automata.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.response.PageHolderResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/responses")
public class ResponseController {

	private final ResponseService responseService;

	@GetMapping("/{responseId}")
	public ResponseEntity<ResponseDto> getResponse(@PathVariable Long responseId) {

		Response response = responseService.getResponseById(responseId);

		ResponseDto responseDto = ResponseMapper.toResponseDto(response);

		return ResponseEntity.ok(responseDto);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<ResponseDto>> getResponses(ResponseFilter filter,
			@PageableDefault(size = 40, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Response> responses = responseService.getResponsesFilteredAndPaged(filter, pageable);

		Page<ResponseDto> cleanedResponses = ResponseMapper.toResponseDtoPage(responses);

		return ResponseEntity.ok(new PageHolderResponse<>(cleanedResponses));

	}

	@DeleteMapping("{responseId}")
	public ResponseEntity<Void> deleteResponse(@PathVariable Long responseId) {

		responseService.deleteResponse(responseId);

		return ResponseEntity.status(204).build();
	}

}
