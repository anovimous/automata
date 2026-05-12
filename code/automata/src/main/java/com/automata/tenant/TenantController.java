package com.automata.tenant;

import java.net.URI;

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
import com.automata.tenant.authentication.Authentication;
import com.automata.tenant.authentication.AuthenticationCreationRequest;
import com.automata.tenant.authentication.AuthenticationDto;
import com.automata.tenant.authentication.AuthenticationMapper;
import com.automata.tenant.authentication.AuthenticationPatchRequest;
import com.automata.tenant.authentication.AuthenticationService;
import com.automata.tenant.common.dto.TenantSummaryResponseDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;

	private final AuthenticationService authService;

	// Tenant specific endpoints ->:

	@GetMapping("/{tenantId}")
	public ResponseEntity<TenantDetailedResponseDto> getTenant(@PathVariable Long tenantId) {

		Tenant tenant = tenantService.getTenantById(tenantId);

		TenantDetailedResponseDto tenantResponse = TenantMapper.toDetailedResponse(tenant);

		return ResponseEntity.ok(tenantResponse);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<TenantSummaryResponseDto>> getTenants(@RequestParam Long hostId,
			@PageableDefault(size = 10, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Tenant> tenants = tenantService.getHostTenantsPage(hostId, pageable);

		Page<TenantSummaryResponseDto> tenantResponses = TenantMapper.toSummaryResponse(tenants);

		return ResponseEntity.ok(new PageHolderResponse<>(tenantResponses));

	}

	@PostMapping("")
	public ResponseEntity<Void> createTenant(@RequestBody TenantCreationRequest request) {

		Tenant toBeCreatedTenant = TenantMapper.toTenant(request);

		Long hostId = request.hostId();

		Tenant createdTenant = tenantService.createTenant(toBeCreatedTenant, hostId);

		return ResponseEntity.status(201).location(URI.create(String.format("/api/tenants/%d", createdTenant.getId())))
				.build();

	}

	@PatchMapping("/{tenantId}")
	public ResponseEntity<Void> patchTenant(@PathVariable Long tenantId, @RequestBody TenantPatchRequest patchRequest) {

		tenantService.patchTenant(tenantId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{tenantId}")
	public ResponseEntity<Void> deleteTenant(@PathVariable Long tenantId) {

		tenantService.deleteTenant(tenantId);

		return ResponseEntity.status(204).build();

	}

	// Tenant authentication specific endpoints ->:

	@GetMapping("/{tenantId}/authentication")

	public ResponseEntity<AuthenticationDto> getAuthentication(@PathVariable Long tenantId) {

		Authentication authentication = authService.getAuthenticationOfTenant(tenantId);

		AuthenticationDto authenticationResponse = AuthenticationMapper.toAuthenticationDto(authentication);

		return ResponseEntity.ok(authenticationResponse);

	}

	@PostMapping("/{tenantId}/authentication")
	public ResponseEntity<Void> createAuthentication(@PathVariable Long tenantId,
			@RequestBody AuthenticationCreationRequest request) {

		Authentication toBeCreatedAuthentication = AuthenticationMapper.toAuthentication(request);

		Authentication createdAuthentication = authService.createAuthentication(toBeCreatedAuthentication, tenantId);

		return ResponseEntity.status(201)
				.location(URI.create(
						String.format("/api/tenants/%d/authentication", createdAuthentication.getTenant().getId())))
				.build();
	}

	@PatchMapping("/{tenantId}/authentication")
	public ResponseEntity<Void> patchAuthentication(@PathVariable Long tenantId,
			@RequestBody AuthenticationPatchRequest patchRequest) {

		authService.patchAuthentication(patchRequest, tenantId);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{tenantId}/authentication")
	public ResponseEntity<Void> deleteAuthentication(@PathVariable Long tenantId, @PathVariable Long authenticationId) {

		authService.deleteAuthentication(tenantId);

		return ResponseEntity.status(204).build();

	}

}
