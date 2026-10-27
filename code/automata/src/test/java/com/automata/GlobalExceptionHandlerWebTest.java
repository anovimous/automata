package com.automata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.automata.testsupport.KnownBug;
import com.automata.testsupport.Mvc;

/**
 * What the HTTP client sees for the errors Spring itself raises (the handlers of the application are tested in {@link GlobalExceptionHandlerTest}):
 * the catch-all {@code @ExceptionHandler(Exception.class)} also catches those, so they must be mapped explicitly or they become a 500.
 */
class GlobalExceptionHandlerWebTest {

	@RestController
	@RequestMapping("/probe")
	static class Probe {

		record Body(String name) {
		}

		@GetMapping("/{id}")
		String byId(@PathVariable Long id) {
			return "ok";
		}

		@GetMapping("/required")
		String required(@RequestParam Long hostId) {
			return "ok";
		}

		@PostMapping("/body")
		String body(@RequestBody Body body) {
			return "ok";
		}

		@GetMapping("/status")
		String status() {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Authentication not found for this tenant");
		}

		@GetMapping("/integrity")
		String integrity() {
			throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
		}
	}

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = Mvc.standalone(new Probe()).build();
	}

	@Test
	void malformedJsonBodyAnswers400() throws Exception {
		mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@KnownBug("BUG-004")
	void aPathVariableOfTheWrongTypeAnswers400() throws Exception {
		mvc.perform(get("/probe/abc")).andExpect(status().isBadRequest());
	}

	@Test
	@KnownBug("BUG-004")
	void aMissingRequiredParameterAnswers400() throws Exception {
		mvc.perform(get("/probe/required")).andExpect(status().isBadRequest());
	}

	@Test
	@KnownBug("BUG-004")
	void aWrongHttpMethodAnswers405() throws Exception {
		mvc.perform(post("/probe/required")).andExpect(status().isMethodNotAllowed());
	}

	@Test
	@KnownBug("BUG-004")
	void aResponseStatusExceptionKeepsItsStatus() throws Exception {
		mvc.perform(get("/probe/status")).andExpect(status().isNotFound());
	}

	@Test
	@KnownBug("BUG-008")
	void aDatabaseConstraintViolationAnswers409() throws Exception {
		mvc.perform(get("/probe/integrity")).andExpect(status().isConflict());
	}
}
