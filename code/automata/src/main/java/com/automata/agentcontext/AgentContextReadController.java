package com.automata.agentcontext;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.automata.agentcontext.common.dto.AgentContextTenantResponse;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.common.dto.RawHttpMessage;
import com.automata.request.RequestMapper;
import com.automata.request.RequestService;
import com.automata.request.common.dto.RequestMetadataResponse;
import com.automata.request.common.dto.RequestRawResponse;
import com.automata.response.RawResponseDto;
import com.automata.response.Response;
import com.automata.response.ResponseMetadataDto;
import com.automata.response.ResponseService;
import com.automata.storage.BodyDownloads;
import com.automata.storage.StoredBody;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

// Reads of the requests and responses a context gives access to. A request that is
// not in the context answers 404, like a request that does not exist. The response
// is addressed through its request.
@RestController
@RequestMapping("/api/agent-contexts/uuid/{uuid}")
@RequiredArgsConstructor
public class AgentContextReadController {

	private final AgentContextService contextService;

	private final RequestService requestService;

	private final ResponseService responseService;

	@GetMapping("/resolved-requests")
	public ResponseEntity<List<ResolvedTarget>> getResolvedRequests(@PathVariable UUID uuid) {

		Long contextId = contextService.getContextByUuid(uuid).getId();

		return ResponseEntity.ok(contextService.resolveContextTargets(contextId));

	}

	@GetMapping("/tenants")
	public ResponseEntity<List<AgentContextTenantResponse>> getTenants(@PathVariable UUID uuid) {

		return ResponseEntity.ok(contextService.getContextTenants(uuid));

	}

	@GetMapping("/requests/{requestId}/metadata")
	public ResponseEntity<RequestMetadataResponse> getRequestMetadata(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		return ResponseEntity.ok(requestService.getRequestMetadata(requestId));

	}

	@GetMapping("/requests/{requestId}/raw")
	public ResponseEntity<RequestRawResponse> getRawRequest(@PathVariable UUID uuid, @PathVariable Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		RawHttpMessage rawRequest = requestService.getRawRequest(requestId);

		Response response = responseService.getResponseOfRequest(requestId);

		return ResponseEntity.ok(RequestMapper.toRawResponse(rawRequest, response != null ? response.getId() : null));

	}

	@GetMapping("/requests/{requestId}/body/stream")
	public ResponseEntity<InputStreamResource> streamRequestBody(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		StoredBody body = requestService.getRequestBody(requestId);

		return BodyDownloads.attachment(body, "request-" + requestId);

	}

	@GetMapping("/requests/{requestId}/response/metadata")
	public ResponseEntity<ResponseMetadataDto> getResponseMetadata(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		Long responseId = this.responseIdOfRequestInContext(uuid, requestId);

		return ResponseEntity.ok(responseService.getResponseMetadata(responseId));

	}

	@GetMapping("/requests/{requestId}/response/raw")
	public ResponseEntity<RawResponseDto> getRawResponse(@PathVariable UUID uuid, @PathVariable Long requestId) {

		Long responseId = this.responseIdOfRequestInContext(uuid, requestId);

		RawHttpMessage rawResponse = responseService.getRawResponse(responseId);

		return ResponseEntity.ok(new RawResponseDto(rawResponse.rawHeadBase64(), rawResponse.rawBodyBase64(),
				rawResponse.bodyStoredAsFile()));

	}

	@GetMapping("/requests/{requestId}/response/body/stream")
	public ResponseEntity<InputStreamResource> streamResponseBody(@PathVariable UUID uuid,
			@PathVariable Long requestId) {

		Long responseId = this.responseIdOfRequestInContext(uuid, requestId);

		StoredBody body = responseService.getResponseBody(responseId);

		return BodyDownloads.attachment(body, "response-" + responseId);

	}

	private Long responseIdOfRequestInContext(UUID uuid, Long requestId) {

		contextService.requireRequestInContext(uuid, requestId);

		Response response = responseService.getResponseOfRequest(requestId);

		if (response == null)
			throw new EntityNotFoundException("Response not found");

		return response.getId();

	}

}
