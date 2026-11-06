package com.automata.job.infra;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.automata.job.service.HttpJobStateService;
import com.automata.job.service.HttpJobsSynchronizationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpringScheduler {

	private final HttpJobsSynchronizationService syncService;

	private final HttpJobStateService stateService;

	@Scheduled(fixedDelay = 60000)
	public void synchronizeHttpJobs() {

		syncService.syncToQueueJobs();

	}

	// The first run waits, so that after the server was down the workers get the
	// time to get in touch again before their jobs are taken for lost.
	@Scheduled(initialDelay = 300000, fixedDelay = 60000)
	public void markJobsWithLostContact() {

		try {
			stateService.markJobsWithLostContact(Instant.now(), HttpJobStateService.LOST_CONTACT_AFTER);
		} catch (RuntimeException e) {
			// a job changed in between, the next run finds it again
			log.warn("Marking the jobs with lost contact failed", e);
		}

	}

}
