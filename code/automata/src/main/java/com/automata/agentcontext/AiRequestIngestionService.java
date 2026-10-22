package com.automata.agentcontext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.agentcontext.common.dto.AiRequestAdditionDto;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.Request;
import com.automata.request.RequestService;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.enums.Source;
import com.automata.tenant.Tenant;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

// Adds a request (and its response) for the AI agent the way the Burp adapter does:
// the host is found by its name. A tenant can only be set through an agent context,
// and has to be one of the tenants of that context.
@Service
@RequiredArgsConstructor
public class AiRequestIngestionService {

	private final HostRepository hostRepo;

	private final AgentContextRepository contextRepo;

	private final RequestService requestService;

	@Transactional
	public Request addRawRequest(AiRequestAdditionDto dto) {

		Host host = hostRepo.findByHost(dto.host()).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		this.requireTenantAllowed(dto, host);

		return requestService.addRawRequestWithResponse(new RawRequestAdditionDto(dto.requestBase64(), Source.AI,
				host.getId(), dto.tenantId(), dto.base64Response()));

	}

	private void requireTenantAllowed(AiRequestAdditionDto dto, Host host) {

		if (dto.tenantId() == null)
			return;

		if (dto.agentContextUuid() == null)
			throw new IllegalArgumentException("agentContextUuid is required when a tenantId is given");

		AgentContext context = contextRepo.findByUuid(dto.agentContextUuid())
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));

		Tenant tenant = context.getTenants().stream().filter(t -> t.getId().equals(dto.tenantId())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Tenant is not in the agent context"));

		if (!tenant.getHost().getId().equals(host.getId()))
			throw new IllegalArgumentException("Tenant does not belong to the host of the request");

	}

}
