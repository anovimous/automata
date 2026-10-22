package com.automata.agentcontext;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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

import com.automata.agentcontext.common.dto.AgentContextCreationRequest;
import com.automata.agentcontext.common.dto.AgentContextDetailedResponse;
import com.automata.agentcontext.common.dto.AgentContextItemAddRequest;
import com.automata.agentcontext.common.dto.AgentContextItemResponse;
import com.automata.agentcontext.common.dto.AgentContextPatchRequest;
import com.automata.agentcontext.common.dto.AgentContextSummaryResponse;
import com.automata.agentcontext.common.dto.AgentContextTenantAddRequest;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.common.dto.response.PageHolderResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/agent-contexts")
@RequiredArgsConstructor
public class AgentContextController {

	private final AgentContextService contextService;

	@PostMapping("")
	public ResponseEntity<Void> createContext(@RequestBody AgentContextCreationRequest request) {

		AgentContext context = contextService.createContext(request);

		return ResponseEntity.created(URI.create(String.format("/api/agent-contexts/%d", context.getId()))).build();

	}

	@GetMapping("/{contextId}")
	public ResponseEntity<AgentContextDetailedResponse> getContext(@PathVariable Long contextId) {

		AgentContext context = contextService.getContext(contextId);

		return ResponseEntity.ok(AgentContextMapper.toDetailedResponse(context));

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<AgentContextSummaryResponse>> getContexts(
			@RequestParam(required = false) Long hostId,
			@PageableDefault(size = 20, sort = "creationDate", direction = Sort.Direction.DESC) Pageable pageable) {

		Page<AgentContext> contexts = contextService.getContextsByHost(hostId, pageable);

		Page<AgentContextSummaryResponse> responses = contexts.map(AgentContextMapper::toSummaryResponse);

		return ResponseEntity.ok(new PageHolderResponse<>(responses));

	}

	@GetMapping("/uuid/{uuid}")
	public ResponseEntity<AgentContextDetailedResponse> getContextByUuid(@PathVariable UUID uuid) {

		AgentContext context = contextService.getContextByUuid(uuid);

		return ResponseEntity.ok(AgentContextMapper.toDetailedResponse(context));

	}

	@PatchMapping("/{contextId}")
	public ResponseEntity<Void> patchContext(@PathVariable Long contextId,
			@RequestBody AgentContextPatchRequest request) {

		contextService.patchContext(contextId, request);

		return ResponseEntity.status(204).build();

	}

	@DeleteMapping("/{contextId}")
	public ResponseEntity<Void> deleteContext(@PathVariable Long contextId) {

		contextService.deleteContext(contextId);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{contextId}/items")
	public ResponseEntity<AgentContextItemResponse> addItem(@PathVariable Long contextId,
			@RequestBody AgentContextItemAddRequest request) {

		AgentContextItem item = contextService.addItem(contextId, request);

		return ResponseEntity.status(201).body(AgentContextMapper.toItemResponse(item));

	}

	@DeleteMapping("/{contextId}/items/{itemId}")
	public ResponseEntity<Void> removeItem(@PathVariable Long contextId, @PathVariable Long itemId) {

		contextService.removeItem(contextId, itemId);

		return ResponseEntity.status(204).build();

	}

	@PostMapping("/{contextId}/tenants")
	public ResponseEntity<Void> addTenant(@PathVariable Long contextId,
			@RequestBody AgentContextTenantAddRequest request) {

		contextService.addTenant(contextId, request.tenantId());

		return ResponseEntity.status(204).build();

	}

	@DeleteMapping("/{contextId}/tenants/{tenantId}")
	public ResponseEntity<Void> removeTenant(@PathVariable Long contextId, @PathVariable Long tenantId) {

		contextService.removeTenant(contextId, tenantId);

		return ResponseEntity.status(204).build();

	}

	@GetMapping("/{contextId}/resolved-requests")
	public ResponseEntity<List<ResolvedTarget>> getResolvedRequests(@PathVariable Long contextId) {

		List<ResolvedTarget> targets = contextService.resolveContextTargets(contextId);

		return ResponseEntity.ok(targets);

	}

}
