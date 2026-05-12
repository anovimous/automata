package com.automata;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GenericRabbitMQConfig {

	public static final String EXCHANGE = "automataDirectExchange";

	@Bean
	DirectExchange exchange() {
		return new DirectExchange(EXCHANGE);
	}

	@Bean
	MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	RabbitTemplateCustomizer rabbitTemplateCustomizer() {
		return template -> {
			template.setExchange(EXCHANGE);
			template.setMessageConverter(jsonMessageConverter());
		};
	}
}
