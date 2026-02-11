package com.automata.tenant;

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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;

	@GetMapping("/{tenantId}")
	public ResponseEntity<TenantResponse> getTenant(@PathVariable Long tenantId) {

		Tenant tenant = tenantService.getTenantById(tenantId);

		TenantResponse tenantResponse = TenantMapper.toTenantResponse(tenant);

		return ResponseEntity.ok(tenantResponse);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<TenantResponse>> getTenants(@RequestParam Long hostId,
			@PageableDefault(size = 10, sort = "creationDate", direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Tenant> tenants = tenantService.getHostTenantsPage(hostId, pageable);

		Page<TenantResponse> tenantResponses = tenants.map(TenantMapper::toTenantResponse);

		return ResponseEntity.ok(new PageHolderResponse<>(tenantResponses));

	}

	@PostMapping("")
	public ResponseEntity<TenantResponse> createTenant(@RequestBody TenantCreationRequest request) {

		Tenant toBeCreatedTenant = TenantMapper.toTenant(request);

		Long hostId = request.hostId();

		Tenant createdTenant = tenantService.createTenant(toBeCreatedTenant, hostId);

		TenantResponse tenantResponse = TenantMapper.toTenantResponse(createdTenant);

		return ResponseEntity.status(201).body(tenantResponse);

	}

	@PatchMapping("/{tenantId}")
	public ResponseEntity<TenantResponse> patchTenant(@PathVariable Long tenantId,
			@RequestBody TenantPatchRequest patchRequest) {

		Tenant tenant = tenantService.patchTenant(tenantId, patchRequest);

		TenantResponse tenantResponse = TenantMapper.toTenantResponse(tenant);

		return ResponseEntity.ok(tenantResponse);
	}

	@DeleteMapping("/{tenantId}")
	public ResponseEntity<Void> deleteTenant(@PathVariable Long tenantId) {

		tenantService.deleteTenant(tenantId);

		return ResponseEntity.status(204).build();

	}

}
