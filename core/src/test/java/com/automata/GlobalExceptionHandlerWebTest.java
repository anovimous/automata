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

		@GetMapping("/integrity/{state}")
		String integrityWithState(@PathVariable String state) {
			throw new DataIntegrityViolationException("could not execute statement [secret sql text]",
					new org.hibernate.exception.ConstraintViolationException("detail", new java.sql.SQLException("reason", state), "constraint"));
		}

		enum Kind {
			A, B
		}

		@GetMapping("/kind")
		String kind(@RequestParam Kind kind) {
			return "ok";
		}

		static class Filter {

			private Integer limit;

			public Integer getLimit() {
				return limit;
			}

			public void setLimit(Integer limit) {
				this.limit = limit;
			}
		}

		@GetMapping("/filter")
		String filter(@org.springframework.web.bind.annotation.ModelAttribute Filter filter) {
			return "ok";
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
	void aPathVariableOfTheWrongTypeAnswers400() throws Exception {
		mvc.perform(get("/probe/abc")).andExpect(status().isBadRequest());
	}

	@Test
	void aMissingRequiredParameterAnswers400() throws Exception {
		mvc.perform(get("/probe/required")).andExpect(status().isBadRequest());
	}

	@Test
	void aWrongHttpMethodAnswers405() throws Exception {
		mvc.perform(post("/probe/required")).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void aResponseStatusExceptionKeepsItsStatus() throws Exception {
		mvc.perform(get("/probe/status")).andExpect(status().isNotFound());
	}

	@Test
	void aDatabaseConstraintViolationAnswers409() throws Exception {
		mvc.perform(get("/probe/integrity")).andExpect(status().isConflict());
	}

	@Test
	void aPathVariableOfTheWrongTypeNamesTheParameterAndTheValue() throws Exception {
		mvc.perform(get("/probe/abc")).andExpect(status().isBadRequest())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string("The parameter 'id' has an invalid value 'abc'"));
	}

	@Test
	void aVeryLongInvalidValueIsCutInTheMessage() throws Exception {
		String message = mvc.perform(get("/probe/" + "x".repeat(500))).andExpect(status().isBadRequest()).andReturn().getResponse()
				.getContentAsString();

		org.junit.jupiter.api.Assertions.assertTrue(message.length() < 150, message);
	}

	@Test
	void anUnknownEnumConstantInAQueryAnswers400() throws Exception {
		mvc.perform(get("/probe/kind").param("kind", "NOPE")).andExpect(status().isBadRequest());
		mvc.perform(get("/probe/kind").param("kind", "A")).andExpect(status().isOk());
	}

	@Test
	void aFilterThatCannotBeBoundAnswers400AndNamesTheField() throws Exception {
		mvc.perform(get("/probe/filter").param("limit", "many")).andExpect(status().isBadRequest())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string("The request has an invalid value for 'limit'"));
		mvc.perform(get("/probe/filter").param("limit", "5")).andExpect(status().isOk());
	}

	@Test
	void aWrongHttpMethodAnswers405AndListsTheAllowedOnes() throws Exception {
		mvc.perform(post("/probe/required")).andExpect(status().isMethodNotAllowed())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow", "GET"));
	}

	@Test
	void aResponseStatusExceptionKeepsItsReason() throws Exception {
		mvc.perform(get("/probe/status")).andExpect(status().isNotFound())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("Authentication not found for this tenant"));
	}

	@Test
	void aUniqueViolationAnswers409() throws Exception {
		mvc.perform(get("/probe/integrity/23505")).andExpect(status().isConflict())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("A record with the same unique value already exists"));
	}

	@Test
	void aForeignKeyViolationAnswers409() throws Exception {
		mvc.perform(get("/probe/integrity/23503")).andExpect(status().isConflict());
	}

	@Test
	void aMissingRequiredValueAnswers400() throws Exception {
		mvc.perform(get("/probe/integrity/23502")).andExpect(status().isBadRequest())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("A required value is missing"));
	}

	@Test
	void aCheckViolationAnswers400() throws Exception {
		mvc.perform(get("/probe/integrity/23514")).andExpect(status().isBadRequest());
	}

	@Test
	void aValueThatIsTooLongOrInvalidAnswers400() throws Exception {
		mvc.perform(get("/probe/integrity/22001")).andExpect(status().isBadRequest())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("A value is invalid or too long"));
		mvc.perform(get("/probe/integrity/22021")).andExpect(status().isBadRequest());
	}

	@Test
	void anyOtherDatabaseRefusalAnswers409() throws Exception {
		mvc.perform(get("/probe/integrity/40001")).andExpect(status().isConflict());
	}

	@Test
	void theMessageNeverRepeatsTheSqlOfTheDatabase() throws Exception {
		String message = mvc.perform(get("/probe/integrity/23505")).andReturn().getResponse().getContentAsString();

		org.junit.jupiter.api.Assertions.assertFalse(message.contains("secret sql text"), message);
	}
}
