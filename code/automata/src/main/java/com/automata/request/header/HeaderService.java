package com.automata.request.header;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.host.Host;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HeaderService {

	private final HeaderRepository headerRepo;

	@Transactional
	public void addHeaders(List<Header> headers, Host host) {

		headers.forEach(header -> {
			header.setHost(host);
			header.getPrimaryKey().setHostId(host.getId());
		});

		headerRepo.saveAll(headers);

	}

}
