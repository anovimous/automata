package com.automata.host;

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
import com.automata.host.common.dto.PatchHostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hosts")
@RequiredArgsConstructor
public class HostController {

	private final HostService hostService;

	@GetMapping("/{hostId}")
	public ResponseEntity<Host> getHost(@PathVariable Long hostId) {

		Host host = hostService.getHostById(hostId);

		return ResponseEntity.ok(host);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<Host>> getHosts(@RequestParam(defaultValue = "") String query,
			@RequestParam Long programId,
			@PageableDefault(size = 10, sort = "insertionDate", direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Host> hosts;

		if (programId == null)
			hosts = hostService.getHostsPagedAndFilteredOnQueryString(query, pageable);
		else
			hosts = hostService.getProgramHostsPagedAndFilteredOnQueryString(programId, query, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(hosts));

	}

	@PostMapping("")
	public ResponseEntity<Host> createHost(@RequestBody HostCreationRequest request) throws Exception {

		ObjectMapper mapper = new ObjectMapper();

		Host toBeCreatedHost = mapper.convertValue(request, Host.class);

		Long programId = request.programId();

		Host createdHost = hostService.createHost(toBeCreatedHost, programId);

		return ResponseEntity.status(201).body(createdHost);

	}

	@PatchMapping("/{hostId}")
	public ResponseEntity<Host> patchHost(@PathVariable Long hostId, @RequestBody PatchHostRequest patchRequest)
			throws Exception {

		Host host = hostService.patchHost(hostId, patchRequest);

		return ResponseEntity.ok(host);
	}

	@DeleteMapping("/{hostId}")
	public ResponseEntity<Void> deleteHost(@PathVariable Long hostId) {

		return ResponseEntity.status(503).build();

//		waiting for service implementation

//		hostService.deleteHost(hostId);
//		
//		return ResponseEntity.status(204).build();

	}

}
