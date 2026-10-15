package com.automata.response;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.automata.common.dto.RawHttpMessage;
import com.automata.common.utils.Base64Utils;
import com.automata.host.Host;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.header.HeaderService;
import com.automata.storage.BodyObjectStorage;
import com.automata.storage.StoredBody;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResponseServiceBodyStorageTest {

	@Mock
	private ResponseRepository responseRepo;
	@Mock
	private RequestRepository requestRepo;
	@Mock
	private ResponseRawRepository responseRawRepo;
	@Mock
	private HeaderService headerService;
	@Mock
	private BodyPropertyService bodyService;
	@Mock
	private BodyObjectStorage bodyStorage;

	@InjectMocks
	private ResponseService service;

	private Request request;

	@BeforeEach
	void stubRepositories() {
		request = Request.of(5L);
		request.setHost(new Host());
		when(responseRepo.save(any(Response.class))).thenAnswer(invocation -> {
			Response response = invocation.getArgument(0);
			response.setId(21L);
			return response;
		});
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	private ResponseRaw savedRaw() {
		ArgumentCaptor<ResponseRaw> captor = ArgumentCaptor.forClass(ResponseRaw.class);
		verify(responseRawRepo).save(captor.capture());
		return captor.getValue();
	}

	// addResponseToRequest

	@Test
	void addResponse_jsonBody_keepsHeadAndBodyInTheRawTable_andStoresProperties() {
		byte[] head = bytes("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 7\r\n\r\n");
		byte[] body = bytes("{\"a\":1}");
		byte[] raw = new byte[head.length + body.length];
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		service.addResponseToRequest(Base64Utils.encode(raw), request);

		ResponseRaw saved = savedRaw();
		assertArrayEquals(head, Base64Utils.decodeToBytes(saved.getRawHeadBase64()));
		assertArrayEquals(body, Base64Utils.decodeToBytes(saved.getRawBodyBase64()));
		Response response = request.getResponse();
		assertEquals(ResponseContentType.JSON, response.getContentType());
		assertFalse(response.isBodyStoredAsFile());
		assertEquals(body.length, response.getBodySizeBytes());
		verify(bodyService).persistResponseBodyProperties(any(), eq(response));
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addResponse_htmlBody_goesToStorage_andRawKeepsOnlyTheHead() {
		byte[] head = bytes("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n");
		byte[] body = bytes("<html></html>");
		byte[] raw = new byte[head.length + body.length];
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		service.addResponseToRequest(Base64Utils.encode(raw), request);

		ResponseRaw saved = savedRaw();
		assertArrayEquals(head, Base64Utils.decodeToBytes(saved.getRawHeadBase64()));
		assertNull(saved.getRawBodyBase64());
		Response response = request.getResponse();
		assertEquals(ResponseContentType.HTML, response.getContentType());
		assertTrue(response.isBodyStoredAsFile());
		assertEquals(body.length, response.getBodySizeBytes());

		ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
		verify(bodyStorage).storeResponseBody(eq(21L), stored.capture(), eq("text/html; charset=utf-8"));
		assertArrayEquals(body, stored.getValue());
	}

	@Test
	void addResponse_binaryBody_isStoredUntouched() {
		byte[] head = bytes("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n\r\n");
		byte[] body = { (byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xFF };
		byte[] raw = new byte[head.length + body.length];
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		service.addResponseToRequest(Base64Utils.encode(raw), request);

		ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
		verify(bodyStorage).storeResponseBody(eq(21L), stored.capture(), eq("image/png"));
		assertArrayEquals(body, stored.getValue());
	}

	@Test
	void addResponse_noBody_isStillStored_withoutAFile() {
		service.addResponseToRequest(Base64Utils.encode(bytes("HTTP/1.1 204 No Content\r\n\r\n")), request);

		assertNull(savedRaw().getRawBodyBase64());
		Response response = request.getResponse();
		assertEquals(204, response.getStatusCode());
		assertFalse(response.isBodyStoredAsFile());
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addResponse_malformedJson_isRejected_andNothingIsStored() {
		byte[] raw = bytes("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"a\":");

		assertThrows(IllegalArgumentException.class,
				() -> service.addResponseToRequest(Base64Utils.encode(raw), request));

		verify(responseRepo, never()).save(any());
		verify(responseRawRepo, never()).save(any());
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addResponse_nullResponse_doesNothing() {
		service.addResponseToRequest(null, request);

		verifyNoInteractions(responseRepo, responseRawRepo, bodyStorage);
	}

	// getRawResponse

	private ResponseRaw rawOf(boolean storedAsFile, String head, String body) {
		Response response = new Response();
		response.setBodyStoredAsFile(storedAsFile);
		return new ResponseRaw(response, head, body);
	}

	@Test
	void getRawResponse_inlineBody() {
		when(responseRawRepo.findById(8L)).thenReturn(Optional.of(rawOf(false, "aA==", "Yg==")));

		assertEquals(new RawHttpMessage("aA==", "Yg==", false), service.getRawResponse(8L));
	}

	@Test
	void getRawResponse_bodyStoredAsFile() {
		when(responseRawRepo.findById(8L)).thenReturn(Optional.of(rawOf(true, "aA==", null)));

		assertEquals(new RawHttpMessage("aA==", null, true), service.getRawResponse(8L));
	}

	@Test
	void getRawResponse_unknownResponse_isNotFound() {
		when(responseRawRepo.findById(8L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getRawResponse(8L));
	}

	// getResponseBodyFile

	@Test
	void getResponseBodyFile_bodyStoredAsFile_readsFromStorage() {
		Response response = new Response();
		response.setBodyStoredAsFile(true);
		when(responseRepo.findById(8L)).thenReturn(Optional.of(response));
		StoredBody stored = new StoredBody(null, "text/html", 1);
		when(bodyStorage.downloadResponseBody(8L)).thenReturn(stored);

		assertSame(stored, service.getResponseBodyFile(8L));
	}

	@Test
	void getResponseBodyFile_noFile_isNotFound_withoutTouchingStorage() {
		when(responseRepo.findById(8L)).thenReturn(Optional.of(new Response()));

		assertThrows(EntityNotFoundException.class, () -> service.getResponseBodyFile(8L));

		verifyNoInteractions(bodyStorage);
	}

	@Test
	void getResponseBodyFile_unknownResponse_isNotFound() {
		when(responseRepo.findById(8L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getResponseBodyFile(8L));
	}

}
