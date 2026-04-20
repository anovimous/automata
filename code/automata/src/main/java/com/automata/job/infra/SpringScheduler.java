package com.automata.job.infra;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.automata.job.service.HttpJobsSynchronizationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SpringScheduler {

	private final HttpJobsSynchronizationService syncService;

	@Scheduled(fixedDelay = 60000)
	public void synchronizeHttpJobs() {

		syncService.syncToQueueJobs();

	}

}
