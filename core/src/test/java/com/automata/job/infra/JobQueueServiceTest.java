package com.automata.job.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;

import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.valueobject.HttpJobInternalDto;
import com.automata.job.domain.valueobject.HttpJobQueueMetadata;
import com.automata.routine.common.enums.Duration;

@ExtendWith(MockitoExtension.class)
class JobQueueServiceTest {

	@Mock
	AmqpTemplate rabbit;

	@InjectMocks
	JobQueueService service;

	private HttpJobInternalDto job(int priority) {
		return new HttpJobInternalDto(7L, Instant.parse("2026-10-20T12:00:00Z"), HttpJobScope.NARROW, Duration.values()[0],
				priority, 10, 1L, 2L);
	}

	private int publishedPriority(int jobPriority) throws Exception {
		service.queueHttpJob(job(jobPriority), "fuzz");

		ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
		verify(rabbit).convertAndSend(eq("jobs.http.ready"), eq(new HttpJobQueueMetadata(7L, "fuzz")),
				processor.capture());

		Message message = new Message(new byte[0], new MessageProperties());
		return processor.getValue().postProcessMessage(message).getMessageProperties().getPriority();
	}

	@Test
	void jobIsPublishedWithTheRoutingKeyOfTheQueueBinding() throws Exception {
		publishedPriority(1);

		verify(rabbit).convertAndSend(eq(HttpJobRabbitMQConfig.ROUTING_KEY), any(HttpJobQueueMetadata.class),
				any(MessagePostProcessor.class));
	}

	@Test
	void eagerJobIsPublishedWithTheHighestPriority() throws Exception {
		assertEquals(2, publishedPriority(4));
	}

	@Test
	void lazyJobIsPublishedWithTheLowerPriority() throws Exception {
		assertEquals(1, publishedPriority(2));
	}

}
