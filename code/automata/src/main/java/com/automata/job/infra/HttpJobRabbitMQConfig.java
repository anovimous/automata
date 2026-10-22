package com.automata.job.infra;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class HttpJobRabbitMQConfig {

	// The worker (orchestrator) declares the same queue with the same arguments,
	// and the broker refuses a declaration that differs. A change here has to be
	// made in the orchestrator too (orchestrator/internal/queue).
	public static final String QUEUE = "jobs.http.queue";

	public static final String ROUTING_KEY = "jobs.http.ready";

	public static final int MAX_PRIORITY = 2;

	// the priorities of the messages: the eager jobs go before the others
	public static final int EAGER_MESSAGE_PRIORITY = MAX_PRIORITY;

	public static final int LAZY_MESSAGE_PRIORITY = 1;

	private final DirectExchange directExchange;

	@Bean
	Queue httpJobsQueue() {
		return QueueBuilder.durable(QUEUE).withArgument("x-max-priority", MAX_PRIORITY).build();
	}

	@Bean
	Binding httpJobsBinding() {
		return BindingBuilder.bind(httpJobsQueue()).to(directExchange).with(ROUTING_KEY);
	}

}
