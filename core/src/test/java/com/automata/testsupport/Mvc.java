package com.automata.testsupport;

import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.SortHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;

import com.automata.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Standalone MockMvc set-up that behaves like the application: Boot-like Jackson, Pageable arguments, and the real exception handler. */
public final class Mvc {

	private Mvc() {
	}

	/** A Jackson mapper configured like the one of Spring Boot (unknown properties ignored, Java 8 and time modules). */
	public static ObjectMapper mapper() {
		return Jackson2ObjectMapperBuilder.json().failOnUnknownProperties(false).build();
	}

	public static StandaloneMockMvcBuilder standalone(Object... controllers) {
		return MockMvcBuilders.standaloneSetup(controllers).setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(new SortHandlerMethodArgumentResolver()));
	}
}
