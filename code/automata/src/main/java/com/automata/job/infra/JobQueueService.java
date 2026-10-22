package com.automata.job.infra;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

import com.automata.job.domain.policy.JobDispatchOrder;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.HttpJobQueueMetadata;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobQueueService {

	private final AmqpTemplate rabbit;

	public void queueHttpJob(HttpJobInternalDto jobDto, String routineKey) {

		HttpJobQueueMetadata metadata = new HttpJobQueueMetadata(jobDto.id(), routineKey);

		rabbit.convertAndSend(HttpJobRabbitMQConfig.ROUTING_KEY, metadata, message -> {
			if (JobDispatchOrder.isEager(jobDto.priority()))
				message.getMessageProperties().setPriority(HttpJobRabbitMQConfig.EAGER_MESSAGE_PRIORITY);
			else
				message.getMessageProperties().setPriority(HttpJobRabbitMQConfig.LAZY_MESSAGE_PRIORITY);
			return message;
		});

	}

}
