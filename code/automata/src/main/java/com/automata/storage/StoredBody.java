package com.automata.storage;

import java.io.InputStream;

// A body file read back from object storage. The stream must be closed by the reader.
public record StoredBody(InputStream stream, String contentType, long size) {

}
