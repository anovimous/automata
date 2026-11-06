package com.automata.request.header;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.automata.request.common.enums.RequestContentType;
import com.automata.response.ResponseContentType;

class HeaderUtilsTest {

	// extractMimeType

	@Test
	void extractMimeType_dropsParametersAndLowercases() {
		assertEquals("application/json", HeaderUtils.extractMimeType("Application/JSON; charset=UTF-8"));
	}

	@Test
	void extractMimeType_multipartBoundaryIsDropped() {
		assertEquals("multipart/form-data", HeaderUtils.extractMimeType("multipart/form-data; boundary=----abc"));
	}

	@Test
	void extractMimeType_trimsWhitespace() {
		assertEquals("text/html", HeaderUtils.extractMimeType("  text/html  "));
	}

	@Test
	void extractMimeType_nullIsEmpty() {
		assertEquals("", HeaderUtils.extractMimeType(null));
	}

	// request content types

	@Test
	void request_jsonAndJsonSuffixTypes() {
		assertEquals(RequestContentType.JSON, HeaderUtils.detectRequestContentType("application/json"));
		assertEquals(RequestContentType.JSON, HeaderUtils.detectRequestContentType("application/vnd.api+json"));
		assertEquals(RequestContentType.JSON, HeaderUtils.detectRequestContentType("application/problem+json"));
		assertEquals(RequestContentType.JSON, HeaderUtils.detectRequestContentType("application/json; charset=utf-8"));
	}

	@Test
	void request_formAndMultipart() {
		assertEquals(RequestContentType.FORM,
				HeaderUtils.detectRequestContentType("application/x-www-form-urlencoded"));
		assertEquals(RequestContentType.MULTIPART,
				HeaderUtils.detectRequestContentType("multipart/form-data; boundary=x"));
	}

	@Test
	void request_textLikeTypes() {
		assertEquals(RequestContentType.XML, HeaderUtils.detectRequestContentType("application/xml"));
		assertEquals(RequestContentType.XML, HeaderUtils.detectRequestContentType("text/xml"));
		assertEquals(RequestContentType.XML, HeaderUtils.detectRequestContentType("application/soap+xml"));
		assertEquals(RequestContentType.HTML, HeaderUtils.detectRequestContentType("text/html"));
		assertEquals(RequestContentType.HTML, HeaderUtils.detectRequestContentType("application/xhtml+xml"));
		assertEquals(RequestContentType.JS, HeaderUtils.detectRequestContentType("text/javascript"));
		assertEquals(RequestContentType.CSS, HeaderUtils.detectRequestContentType("text/css"));
		assertEquals(RequestContentType.TEXT, HeaderUtils.detectRequestContentType("text/plain"));
		assertEquals(RequestContentType.TEXT, HeaderUtils.detectRequestContentType("text/csv"));
	}

	@Test
	void request_binaryTypes() {
		assertEquals(RequestContentType.IMAGE, HeaderUtils.detectRequestContentType("image/png"));
		assertEquals(RequestContentType.IMAGE, HeaderUtils.detectRequestContentType("image/svg+xml"));
		assertEquals(RequestContentType.AUDIO, HeaderUtils.detectRequestContentType("audio/mpeg"));
		assertEquals(RequestContentType.VIDEO, HeaderUtils.detectRequestContentType("video/mp4"));
		assertEquals(RequestContentType.PDF, HeaderUtils.detectRequestContentType("application/pdf"));
		assertEquals(RequestContentType.ARCHIVE, HeaderUtils.detectRequestContentType("application/zip"));
		assertEquals(RequestContentType.OCTET_STREAM,
				HeaderUtils.detectRequestContentType("application/octet-stream"));
	}

	@Test
	void request_unknownOrBlankIsOther() {
		assertEquals(RequestContentType.OTHER, HeaderUtils.detectRequestContentType("application/x-weird"));
		assertEquals(RequestContentType.OTHER, HeaderUtils.detectRequestContentType(""));
		assertEquals(RequestContentType.OTHER, HeaderUtils.detectRequestContentType(null));
	}

	// response content types

	@Test
	void response_jsonAndJsonSuffixTypes() {
		assertEquals(ResponseContentType.JSON, HeaderUtils.detectResponseContentType("application/json"));
		assertEquals(ResponseContentType.JSON, HeaderUtils.detectResponseContentType("application/hal+json"));
	}

	@Test
	void response_otherKnownTypes() {
		assertEquals(ResponseContentType.HTML, HeaderUtils.detectResponseContentType("text/html"));
		assertEquals(ResponseContentType.XML, HeaderUtils.detectResponseContentType("application/xml"));
		assertEquals(ResponseContentType.JS, HeaderUtils.detectResponseContentType("application/javascript"));
		assertEquals(ResponseContentType.CSS, HeaderUtils.detectResponseContentType("text/css"));
		assertEquals(ResponseContentType.TEXT, HeaderUtils.detectResponseContentType("text/plain"));
		assertEquals(ResponseContentType.IMAGE, HeaderUtils.detectResponseContentType("image/jpeg"));
		assertEquals(ResponseContentType.PDF, HeaderUtils.detectResponseContentType("application/pdf"));
		assertEquals(ResponseContentType.OCTET_STREAM,
				HeaderUtils.detectResponseContentType("application/octet-stream"));
	}

	@Test
	void response_typesWithoutAnEnumValueAreOther() {
		// FORM and MULTIPART only exist for requests
		assertEquals(ResponseContentType.OTHER,
				HeaderUtils.detectResponseContentType("application/x-www-form-urlencoded"));
		assertEquals(ResponseContentType.OTHER, HeaderUtils.detectResponseContentType("multipart/byteranges"));
		assertEquals(ResponseContentType.OTHER, HeaderUtils.detectResponseContentType("application/x-weird"));
	}

}
