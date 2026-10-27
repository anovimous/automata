package com.automata.response;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import com.automata.request.RequestRepository;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.header.HeaderService;
import com.automata.storage.BodyObjectStorage;
import com.automata.testsupport.KnownBug;
import com.automata.testsupport.Mvc;

/** DELETE /api/responses/{id} must not claim a success it did not do. */
class ResponseDeleteTest {

	private final ResponseService service = new ResponseService(mock(ResponseRepository.class), mock(RequestRepository.class),
			mock(ResponseRawRepository.class), mock(HeaderService.class), mock(BodyPropertyService.class), mock(BodyObjectStorage.class));

	private final MockMvc mvc = Mvc.standalone(new ResponseController(service)).build();

	@Test
	@KnownBug("BUG-003")
	void deleteDoesNotAnswer204WhileNothingIsDeleted() throws Exception {
		// today the method is empty, so the client is told the response is gone while it is still there
		mvc.perform(delete("/api/responses/5")).andExpect(status().isNotImplemented());
	}

	@Test
	@KnownBug("BUG-003")
	void deleteOfTheServiceIsEitherImplementedOrSaysSo() {
		assertThrows(RuntimeException.class, () -> service.deleteResponse(5L));
	}
}
