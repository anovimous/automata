package com.automata.e2e;

import java.util.ArrayList;
import java.util.List;

import com.automata.common.dto.BodyStorage;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;

/** What the parsing of one request must produce: the parts that are stored and the views that are read back. */
public final class Expected {

	Method method = Method.GET;

	String version = "HTTP/1.1";

	String path;

	String extension;

	final List<String> vars = new ArrayList<>();

	final List<String> query = new ArrayList<>();

	final List<String> props = new ArrayList<>();

	RequestContentType contentType;

	BodyStorage storage = BodyStorage.NONE;

	private Expected() {
	}

	/** @param computatedPath the path with its numeric and GUID segments abstracted */
	public static Expected path(String computatedPath) {
		Expected expected = new Expected();
		expected.path = computatedPath;
		return expected;
	}

	public Expected method(Method method) {
		this.method = method;
		return this;
	}

	public Expected version(String version) {
		this.version = version;
		return this;
	}

	public Expected extension(String extension) {
		this.extension = extension;
		return this;
	}

	/** Path variables as "index:TYPE=value". */
	public Expected vars(String... vars) {
		this.vars.addAll(List.of(vars));
		return this;
	}

	/** Query parameters in the order they were sent, as "name=value" (value already decoded). */
	public Expected query(String... query) {
		this.query.addAll(List.of(query));
		return this;
	}

	/** Body properties as "fullPath=value:TYPE", in any order. */
	public Expected props(String... props) {
		this.props.addAll(List.of(props));
		return this;
	}

	public Expected contentType(RequestContentType contentType) {
		this.contentType = contentType;
		return this;
	}

	public Expected storage(BodyStorage storage) {
		this.storage = storage;
		return this;
	}

	public Expected inline(RequestContentType contentType) {
		this.contentType = contentType;
		this.storage = BodyStorage.INLINE;
		return this;
	}

}
