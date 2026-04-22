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
import com.automata.tenant.authentication.Authentication;
import com.automata.tenant.authentication.AuthenticationCreationRequest;
import com.automata.tenant.authentication.AuthenticationDto;
import com.automata.tenant.authentication.AuthenticationMapper;
import com.automata.tenant.authentication.AuthenticationPatchRequest;
import com.automata.tenant.authentication.AuthenticationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

	private final TenantService tenantService;

	private final AuthenticationService authService;

	// Tenant specific endpoints ->:

	@GetMapping("/{tenantId}")
	public ResponseEntity<TenantResponse> getTenant(@PathVariable Long tenantId) {

		Tenant tenant = tenantService.getTenantById(tenantId);

		TenantResponse tenantResponse = TenantMapper.toTenantResponse(tenant);

		return ResponseEntity.ok(tenantResponse);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<TenantResponse>> getTenants(@RequestParam Long hostId,
			@PageableDefault(size = 10, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

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

	// Tenant authentication specific endpoints ->:

	@GetMapping("/{tenantId}/authentications/{authenticationId}")

	public ResponseEntity<AuthenticationDto> getAuthentication(@PathVariable Long tenantId,
			@PathVariable Long authenticationId) {

		Authentication authentication = authService.getAuthenticationById(authenticationId, tenantId);

		AuthenticationDto authenticationResponse = AuthenticationMapper.toAuthenticationDto(authentication);

		return ResponseEntity.ok(authenticationResponse);

	}

	@GetMapping("/{tenantId}/authentications")
	public ResponseEntity<PageHolderResponse<AuthenticationDto>> getAuthentications(@PathVariable Long tenantId,
			@PageableDefault(size = 10, sort = "creationDate", direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Authentication> authentications = authService.getTenantAuthenticationsPage(tenantId, pageable);

		Page<AuthenticationDto> authenticationResponses = authentications
				.map(AuthenticationMapper::toAuthenticationDto);

		return ResponseEntity.ok(new PageHolderResponse<>(authenticationResponses));

	}

	@PostMapping("/{tenantId}/authentications")
	public ResponseEntity<AuthenticationDto> createAuthentication(@PathVariable Long tenantId,
			@RequestBody AuthenticationCreationRequest request) {

		Authentication toBeCreatedAuthentication = AuthenticationMapper.toAuthentication(request);

		Authentication createdAuthentication = authService.createAuthentication(toBeCreatedAuthentication, tenantId);

		AuthenticationDto authenticationResponse = AuthenticationMapper.toAuthenticationDto(createdAuthentication);

		return ResponseEntity.status(201).body(authenticationResponse);

	}

	@PatchMapping("/{tenantId}/authentications/{authenticationId}")
	public ResponseEntity<AuthenticationDto> patchAuthentication(@PathVariable Long tenantId,
			@PathVariable Long authenticationId, @RequestBody AuthenticationPatchRequest patchRequest) {

		Authentication authentication = authService.patchAuthentication(authenticationId, patchRequest, tenantId);

		AuthenticationDto authenticationResponse = AuthenticationMapper.toAuthenticationDto(authentication);

		return ResponseEntity.ok(authenticationResponse);
	}

	@DeleteMapping("/{tenantId}/authentications/{authenticationId}")
	public ResponseEntity<Void> deleteAuthentication(@PathVariable Long tenantId, @PathVariable Long authenticationId) {

		authService.deleteAuthentication(authenticationId, tenantId);

		return ResponseEntity.status(204).build();

	}

}
