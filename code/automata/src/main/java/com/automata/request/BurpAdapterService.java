package com.automata.request;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.enums.Source;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BurpAdapterService {

	private final HostRepository hostRepo;

	private final RequestService requestService;

	@Transactional
	public void addRawRequest(String requestBase64, String host, String responseBase64) {

		Host requestHost = hostRepo.findByHost(host).orElseThrow(() -> new EntityNotFoundException("Host not found"));

		requestService.addRawRequestWithResponse(
				new RawRequestAdditionDto(requestBase64, Source.BURP, requestHost.getId(), null, responseBase64));

	}

}
