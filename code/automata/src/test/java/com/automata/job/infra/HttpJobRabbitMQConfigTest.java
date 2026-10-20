package com.automata.job.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class HttpJobRabbitMQConfigTest {

	private final DirectExchange exchange = new DirectExchange("testExchange");

	private final HttpJobRabbitMQConfig config = new HttpJobRabbitMQConfig(exchange);

	@Test
	void queueIsDurableWithTheNameAndPriorityTheWorkerDeclares() {
		Queue queue = config.httpJobsQueue();

		assertEquals("jobs.http.queue", queue.getName());
		assertTrue(queue.isDurable());
		assertEquals(2, queue.getArguments().get("x-max-priority"));
		assertEquals(1, queue.getArguments().size());
	}

	@Test
	void bindingConnectsTheQueueToTheExchangeWithTheRoutingKeyOfThePublisher() {
		Binding binding = config.httpJobsBinding();

		assertEquals("jobs.http.queue", binding.getDestination());
		assertEquals("testExchange", binding.getExchange());
		assertEquals("jobs.http.ready", binding.getRoutingKey());
	}

	@Test
	void eagerMessagesHaveTheHighestPriorityTheQueueKeeps() {
		assertEquals(HttpJobRabbitMQConfig.MAX_PRIORITY, HttpJobRabbitMQConfig.EAGER_MESSAGE_PRIORITY);
		assertTrue(HttpJobRabbitMQConfig.LAZY_MESSAGE_PRIORITY < HttpJobRabbitMQConfig.EAGER_MESSAGE_PRIORITY);
	}

}
