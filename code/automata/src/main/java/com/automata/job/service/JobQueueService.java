package com.automata.job.service;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.HttpJobQueueMetadata;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobQueueService {

	private final AmqpTemplate rabbit;

	public void queueHttpJob(HttpJobInternalDto jobDto, String routineKey) {

		HttpJobQueueMetadata metadata = new HttpJobQueueMetadata(jobDto.id(), routineKey);

		rabbit.convertAndSend("jobs.http.ready", metadata, message -> {
			if (jobDto.priority() > 3)
				message.getMessageProperties().setPriority(2);
			else
				message.getMessageProperties().setPriority(1);
			return message;
		});

	}

}
