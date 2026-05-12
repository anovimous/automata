package com.automata.host;

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
import com.automata.host.common.dto.HostCreationRequest;
import com.automata.host.common.dto.HostDetailedResponse;
import com.automata.host.common.dto.HostSummaryResponse;
import com.automata.host.common.dto.PatchHostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostController {

	private final HostService hostService;

	private final ObjectMapper mapper;

	@GetMapping("/{hostId}")
	public ResponseEntity<HostDetailedResponse> getHost(@PathVariable Long hostId) {

		Host host = hostService.getHostById(hostId);

		HostDetailedResponse response = HostMapper.toDetailedResponse(host);

		return ResponseEntity.ok(response);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<HostSummaryResponse>> getHosts(
			@RequestParam(defaultValue = "") String query, @RequestParam Long programId,
			@PageableDefault(size = 10, sort = {
					"insertionDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Host> hosts;

		if (programId == null)
			hosts = hostService.getHostsPagedAndFilteredOnQueryString(query, pageable);
		else
			hosts = hostService.getProgramHostsPagedAndFilteredOnQueryString(programId, query, pageable);

		Page<HostSummaryResponse> hostsResponse = HostMapper.toSummaryResponse(hosts);

		return ResponseEntity.ok(new PageHolderResponse<>(hostsResponse));

	}

	@PostMapping("")
	public ResponseEntity<Void> createHost(@RequestBody HostCreationRequest request) {

		Host toBeCreatedHost = mapper.convertValue(request, Host.class);

		Long programId = request.programId();

		Host host = hostService.createHost(toBeCreatedHost, programId);

		return ResponseEntity.status(201).location(URI.create(String.format("/api/hosts/%d", host.getId()))).build();

	}

	@PatchMapping("/{hostId}")
	public ResponseEntity<Void> patchHost(@PathVariable Long hostId, @RequestBody PatchHostRequest patchRequest)
			throws Exception {

		hostService.patchHost(hostId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{hostId}")
	public ResponseEntity<Void> deleteHost(@PathVariable Long hostId) {

		hostService.deleteHost(hostId);

		return ResponseEntity.status(204).build();

	}

}
