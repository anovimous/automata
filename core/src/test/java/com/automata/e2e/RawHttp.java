package com.automata.e2e;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.automata.common.dto.HeaderEntry;

/**
 * Builds the bytes of a raw HTTP request the way Burp records it: a request line, headers, an empty line and
 * a body that is never touched by a charset. Everything is configurable so that odd requests can be built.
 */
public final class RawHttp {

	public static final String HOST = "api.example.com";

	private String method = "GET";

	private String target = "/";

	private String version = "HTTP/1.1";

	private String eol = "\r\n";

	private boolean hostHeader = true;

	private boolean terminated = true;

	private String requestLineOverride;

	private final List<String> headerLines = new ArrayList<>();

	private final List<HeaderEntry> headers = new ArrayList<>();

	private byte[] body = new byte[0];

	private RawHttp() {
	}

	public static RawHttp of(String method, String target) {
		RawHttp raw = new RawHttp();
		raw.method = method;
		raw.target = target;
		return raw;
	}

	public static RawHttp get(String target) {
		return of("GET", target);
	}

	public static RawHttp post(String target) {
		return of("POST", target);
	}

	public static RawHttp put(String target) {
		return of("PUT", target);
	}

	public static RawHttp patch(String target) {
		return of("PATCH", target);
	}

	public static RawHttp delete(String target) {
		return of("DELETE", target);
	}

	public static RawHttp head(String target) {
		return of("HEAD", target);
	}

	public RawHttp version(String version) {
		this.version = version;
		return this;
	}

	/** Line endings of the head: "\n" for bare LF, "\r\n" (the default) for CRLF. */
	public RawHttp eol(String eol) {
		this.eol = eol;
		return this;
	}

	public RawHttp noHost() {
		this.hostHeader = false;
		return this;
	}

	/** The head has no empty line at its end (a request that is just a start line and headers). */
	public RawHttp unterminated() {
		this.terminated = false;
		return this;
	}

	/** The start line exactly as given, instead of "METHOD TARGET VERSION". */
	public RawHttp requestLine(String requestLine) {
		this.requestLineOverride = requestLine;
		return this;
	}

	public RawHttp header(String name, String value) {
		headerLines.add(name + ": " + value);
		headers.add(new HeaderEntry(name, value));
		return this;
	}

	/** A header line as given, which the expected headers do not know about (a folded or an invalid line). */
	public RawHttp rawHeaderLine(String line) {
		headerLines.add(line);
		return this;
	}

	public RawHttp contentType(String contentType) {
		return header("Content-Type", contentType);
	}

	public RawHttp body(String text) {
		this.body = text.getBytes(StandardCharsets.UTF_8);
		return this;
	}

	public RawHttp body(byte[] bytes) {
		this.body = bytes.clone();
		return this;
	}

	public RawHttp json(String json) {
		return contentType("application/json").body(json);
	}

	public RawHttp form(String form) {
		return contentType("application/x-www-form-urlencoded").body(form);
	}

	public String method() {
		return method;
	}

	public String target() {
		return target;
	}

	public byte[] bodyBytes() {
		return body.clone();
	}

	/** The headers, in order, as the metadata of the request must list them (the Host header first). */
	public List<HeaderEntry> expectedHeaders() {
		List<HeaderEntry> all = new ArrayList<>();
		if (hostHeader)
			all.add(new HeaderEntry("Host", HOST));
		all.addAll(headers);
		return all;
	}

	public byte[] head() {
		StringBuilder head = new StringBuilder();
		head.append(requestLineOverride != null ? requestLineOverride : method + " " + target + " " + version).append(eol);
		if (hostHeader)
			head.append("Host: ").append(HOST).append(eol);
		headerLines.forEach(line -> head.append(line).append(eol));
		if (terminated)
			head.append(eol);
		return head.toString().getBytes(StandardCharsets.UTF_8);
	}

	public byte[] bytes() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(head());
		out.writeBytes(body);
		return out.toByteArray();
	}

	public String base64() {
		return Base64.getEncoder().encodeToString(bytes());
	}

}
