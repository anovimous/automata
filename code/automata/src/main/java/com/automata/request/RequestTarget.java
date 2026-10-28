package com.automata.request;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.automata.request.common.RequestFieldLimits;

/**
 * The path and the query of the target of a request line, exactly as they were
 * sent. They are split by hand and not by {@link java.net.URI}, which rejects
 * what browsers send unencoded in a query ({@code [ ] { } | ^}) and the
 * malformed escapes that Burp records as they were.
 *
 * @param path  without the query and the fragment; may be empty for an absolute
 *              URL without a path
 * @param query without the "?"; null when the target has no "?"
 */
public record RequestTarget(String path, String query) {

	private static final Pattern ABSOLUTE_FORM = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://[^/?#]*(.*)$",
			Pattern.DOTALL);

	/**
	 * @param target the origin-form ("/path?query") or the absolute-form
	 *               ("http://host/path?query") of a request target
	 * @throws IllegalArgumentException with a message for the sender of the request
	 *                                  when the target is neither of them, or has a
	 *                                  space or a control character, which would be
	 *                                  written into the request line of a job
	 */
	public static RequestTarget parse(String target) {

		if (target == null || target.isEmpty())
			throw new IllegalArgumentException("The request target is empty");

		for (int i = 0; i < target.length(); i++) {
			char c = target.charAt(i);
			if (c <= 0x20 || c == 0x7F)
				throw new IllegalArgumentException("The request target '" + RequestFieldLimits.preview(target)
						+ "' contains a space or a control character");
		}

		// a fragment is not sent to a server, but it is not part of the path either
		int fragment = target.indexOf('#');
		String withoutFragment = fragment < 0 ? target : target.substring(0, fragment);

		String pathAndQuery;

		if (withoutFragment.startsWith("/")) {
			pathAndQuery = withoutFragment;
		} else {
			Matcher absolute = ABSOLUTE_FORM.matcher(withoutFragment);
			if (!absolute.matches())
				throw new IllegalArgumentException("The request target '" + RequestFieldLimits.preview(target)
						+ "' must be a path starting with '/' or an absolute URL");
			pathAndQuery = absolute.group(1);
		}

		int question = pathAndQuery.indexOf('?');

		if (question < 0)
			return new RequestTarget(pathAndQuery, null);

		return new RequestTarget(pathAndQuery.substring(0, question), pathAndQuery.substring(question + 1));

	}

}
