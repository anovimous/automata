package com.automata.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

// A body read back for streaming, either from object storage or from the raw table.
// The stream must be closed by the reader.
public record StoredBody(InputStream stream, String contentType, long size) {

	public static StoredBody inline(byte[] body, String contentType) {

		return new StoredBody(new ByteArrayInputStream(body), contentType, body.length);

	}

}
