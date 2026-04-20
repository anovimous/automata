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

	private final DirectExchange directExchange;

	@Bean
	Queue httpJobsQueue() {
		return QueueBuilder.durable("jobs.http.queue").withArgument("x-max-priority", 2).build();
	}

	@Bean
	Binding httpJobsBinding() {
		return BindingBuilder.bind(httpJobsQueue()).to(directExchange).with("jobs.http.ready");
	}

}
