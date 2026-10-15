package com.automata.request;

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
import com.automata.host.HostRepository;
import com.automata.request.body.BodyPropertyService;
import com.automata.request.common.dto.RawRequestAdditionDto;
import com.automata.request.common.enums.RequestContentType;
import com.automata.request.common.enums.Source;
import com.automata.request.comparator.ComparatorRepository;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.request.header.HeaderService;
import com.automata.request.parameter.QueryParameterService;
import com.automata.request.path.PathService;
import com.automata.response.ResponseService;
import com.automata.storage.BodyObjectStorage;
import com.automata.storage.StoredBody;
import com.automata.tenant.TenantRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestServiceBodyStorageTest {

	@Mock
	private RequestRepository requestRepo;
	@Mock
	private RequestEqualitySetRepository equalitySetRepo;
	@Mock
	private TenantRepository tenantRepo;
	@Mock
	private HostRepository hostRepo;
	@Mock
	private ComparatorRepository comparatorRepo;
	@Mock
	private RequestRawRepository requestRawRepo;
	@Mock
	private ResponseService responseService;
	@Mock
	private BodyObjectStorage bodyStorage;
	@Mock
	private RequestEqualityRelationRepository equalityRelationRepo;
	@Mock
	private PathService pathService;
	@Mock
	private QueryParameterService queryParameterService;
	@Mock
	private BodyPropertyService bodyService;
	@Mock
	private HeaderService headerService;

	@InjectMocks
	private RequestService service;

	@BeforeEach
	void stubRepositories() {
		Host host = new Host();
		host.setId(3L);
		when(hostRepo.findById(3L)).thenReturn(Optional.of(host));
		when(requestRepo.save(any(Request.class))).thenAnswer(invocation -> {
			Request request = invocation.getArgument(0);
			request.setId(11L);
			return request;
		});
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	private RawRequestAdditionDto dto(byte[] raw) {
		return new RawRequestAdditionDto(Base64Utils.encode(raw), Source.BURP, 3L, null, null);
	}

	private RequestRaw savedRaw() {
		ArgumentCaptor<RequestRaw> captor = ArgumentCaptor.forClass(RequestRaw.class);
		verify(requestRawRepo).save(captor.capture());
		return captor.getValue();
	}

	// addRawRequest

	@Test
	void addRawRequest_jsonBody_keepsHeadAndBodyInTheRawTable() {
		byte[] head = bytes("POST /api/u HTTP/1.1\r\nHost: a\r\nContent-Type: application/json\r\n\r\n");
		byte[] body = bytes("{\"a\":1}");
		byte[] raw = new byte[head.length + body.length];
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		Request request = service.addRawRequest(dto(raw));

		RequestRaw saved = savedRaw();
		assertSame(request, saved.getRequest());
		assertArrayEquals(head, Base64Utils.decodeToBytes(saved.getRawHeadBase64()));
		assertArrayEquals(body, Base64Utils.decodeToBytes(saved.getRawBodyBase64()));
		assertFalse(request.isBodyStoredAsFile());
		assertEquals(body.length, request.getBodySizeBytes());
		assertEquals(RequestContentType.JSON, request.getContentType());
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addRawRequest_multipartBody_goesToStorage_andRawKeepsOnlyTheHead() {
		byte[] head = bytes("POST /up HTTP/1.1\r\nHost: a\r\nContent-Type: multipart/form-data; boundary=XX\r\n\r\n");
		byte[] body = { '-', '-', 'X', 'X', 13, 10, 0, (byte) 0xFF, (byte) 0xFE };
		byte[] raw = new byte[head.length + body.length];
		System.arraycopy(head, 0, raw, 0, head.length);
		System.arraycopy(body, 0, raw, head.length, body.length);

		Request request = service.addRawRequest(dto(raw));

		RequestRaw saved = savedRaw();
		assertArrayEquals(head, Base64Utils.decodeToBytes(saved.getRawHeadBase64()));
		assertNull(saved.getRawBodyBase64());
		assertTrue(request.isBodyStoredAsFile());
		assertEquals(body.length, request.getBodySizeBytes());
		assertEquals(RequestContentType.MULTIPART, request.getContentType());
		assertEquals(0, request.getNumberOfProperties());

		ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
		verify(bodyStorage).storeRequestBody(eq(11L), stored.capture(), eq("multipart/form-data; boundary=XX"));
		assertArrayEquals(body, stored.getValue());
	}

	@Test
	void addRawRequest_xmlBody_isAccepted_andKeptInTheRawTable() {
		byte[] raw = bytes("POST /soap HTTP/1.1\r\nHost: a\r\nContent-Type: text/xml\r\n\r\n<a/>");

		Request request = service.addRawRequest(dto(raw));

		assertEquals(RequestContentType.XML, request.getContentType());
		assertEquals("<a/>", new String(Base64Utils.decodeToBytes(savedRaw().getRawBodyBase64()),
				StandardCharsets.UTF_8));
		assertFalse(request.isBodyStoredAsFile());
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addRawRequest_noBody_hasNoRawBody() {
		Request request = service.addRawRequest(dto(bytes("GET /a HTTP/1.1\r\nHost: a\r\n\r\n")));

		assertNull(savedRaw().getRawBodyBase64());
		assertEquals(0, request.getBodySizeBytes());
		assertFalse(request.isBodyStoredAsFile());
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addRawRequest_malformedJson_isRejected_andNothingIsStored() {
		byte[] raw = bytes("POST /a HTTP/1.1\r\nHost: a\r\nContent-Type: application/json\r\n\r\n{\"a\":");

		assertThrows(IllegalArgumentException.class, () -> service.addRawRequest(dto(raw)));

		verify(requestRepo, never()).save(any());
		verify(requestRawRepo, never()).save(any());
		verifyNoInteractions(bodyStorage);
	}

	@Test
	void addRawRequest_unknownHost_isNotFound() {
		RawRequestAdditionDto dto = new RawRequestAdditionDto("", Source.BURP, 99L, null, null);

		assertThrows(EntityNotFoundException.class, () -> service.addRawRequest(dto));
	}

	// addRawRequestWithResponse

	@Test
	void addRawRequestWithResponse_addsTheResponseToTheSavedRequest() {
		byte[] raw = bytes("GET /a HTTP/1.1\r\nHost: a\r\n\r\n");
		RawRequestAdditionDto dto = new RawRequestAdditionDto(Base64Utils.encode(raw), Source.BURP, 3L, null,
				"cmVzcA==");

		Request request = service.addRawRequestWithResponse(dto);

		verify(responseService).addResponseToRequest("cmVzcA==", request);
	}

	@Test
	void addRawRequestWithResponse_rejectedResponse_propagates() {
		byte[] raw = bytes("GET /a HTTP/1.1\r\nHost: a\r\n\r\n");
		RawRequestAdditionDto dto = new RawRequestAdditionDto(Base64Utils.encode(raw), Source.BURP, 3L, null, "x");
		org.mockito.Mockito.doThrow(new IllegalArgumentException("Json body malformed")).when(responseService)
				.addResponseToRequest(eq("x"), any(Request.class));

		assertThrows(IllegalArgumentException.class, () -> service.addRawRequestWithResponse(dto));
	}

	// getRawRequest

	private RequestRaw rawOf(boolean storedAsFile, String head, String body) {
		Request request = Request.of(5L);
		request.setBodyStoredAsFile(storedAsFile);
		return new RequestRaw(request, head, body);
	}

	@Test
	void getRawRequest_inlineBody() {
		when(requestRawRepo.findById(5L)).thenReturn(Optional.of(rawOf(false, "aGVhZA==", "Ym9keQ==")));

		RawHttpMessage message = service.getRawRequest(5L);

		assertEquals(new RawHttpMessage("aGVhZA==", "Ym9keQ==", false), message);
	}

	@Test
	void getRawRequest_bodyStoredAsFile_hasNoBodyAndTheFlag() {
		when(requestRawRepo.findById(5L)).thenReturn(Optional.of(rawOf(true, "aGVhZA==", null)));

		assertEquals(new RawHttpMessage("aGVhZA==", null, true), service.getRawRequest(5L));
	}

	@Test
	void getRawRequest_noBody() {
		when(requestRawRepo.findById(5L)).thenReturn(Optional.of(rawOf(false, "aGVhZA==", null)));

		assertEquals(new RawHttpMessage("aGVhZA==", null, false), service.getRawRequest(5L));
	}

	@Test
	void getRawRequest_unknownRequest_isNotFound() {
		when(requestRawRepo.findById(5L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getRawRequest(5L));
	}

	// getRequestBodyFile

	@Test
	void getRequestBodyFile_bodyStoredAsFile_readsFromStorage() {
		Request request = Request.of(5L);
		request.setBodyStoredAsFile(true);
		when(requestRepo.findById(5L)).thenReturn(Optional.of(request));
		StoredBody stored = new StoredBody(null, "a/b", 1);
		when(bodyStorage.downloadRequestBody(5L)).thenReturn(stored);

		assertSame(stored, service.getRequestBodyFile(5L));
	}

	@Test
	void getRequestBodyFile_noFile_isNotFound_withoutTouchingStorage() {
		when(requestRepo.findById(5L)).thenReturn(Optional.of(Request.of(5L)));

		assertThrows(EntityNotFoundException.class, () -> service.getRequestBodyFile(5L));

		verifyNoInteractions(bodyStorage);
	}

	@Test
	void getRequestBodyFile_unknownRequest_isNotFound() {
		when(requestRepo.findById(5L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.getRequestBodyFile(5L));
	}

}
