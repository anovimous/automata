package com.automata.request.header;

import java.util.Set;

import com.automata.request.common.enums.RequestContentType;
import com.automata.response.ResponseContentType;

public abstract class HeaderUtils {

	// Families of mime types, shared by the request and response enums. A family
	// that an enum does not have becomes OTHER there.
	private enum MimeFamily {
		JSON, FORM, MULTIPART, XML, HTML, JS, CSS, TEXT, IMAGE, AUDIO, VIDEO, PDF, ARCHIVE, OCTET_STREAM, OTHER
	}

	private static final Set<String> JS_TYPES = Set.of("application/javascript", "text/javascript",
			"application/x-javascript", "application/ecmascript", "text/ecmascript");

	private static final Set<String> ARCHIVE_TYPES = Set.of("application/zip", "application/gzip",
			"application/x-gzip", "application/x-tar", "application/x-7z-compressed", "application/x-rar-compressed",
			"application/vnd.rar", "application/x-bzip2");

	// "application/json; charset=utf-8" -> "application/json"
	public static String extractMimeType(String contentTypeHeaderValue) {

		if (contentTypeHeaderValue == null)
			return "";

		int parametersStart = contentTypeHeaderValue.indexOf(';');

		String mimeType = parametersStart < 0 ? contentTypeHeaderValue
				: contentTypeHeaderValue.substring(0, parametersStart);

		return mimeType.trim().toLowerCase();

	}

	public static RequestContentType detectRequestContentType(String mimeType) {

		return toEnum(RequestContentType.class, classify(mimeType), RequestContentType.OTHER);

	}

	public static ResponseContentType detectResponseContentType(String mimeType) {

		return toEnum(ResponseContentType.class, classify(mimeType), ResponseContentType.OTHER);

	}

	private static <E extends Enum<E>> E toEnum(Class<E> enumClass, MimeFamily family, E fallback) {

		try {
			return Enum.valueOf(enumClass, family.name());
		} catch (IllegalArgumentException e) {
			return fallback;
		}

	}

	private static MimeFamily classify(String mimeType) {

		String mime = extractMimeType(mimeType);

		if (mime.equals("application/json") || mime.endsWith("+json"))
			return MimeFamily.JSON;

		if (mime.equals("application/x-www-form-urlencoded"))
			return MimeFamily.FORM;

		if (mime.startsWith("multipart/"))
			return MimeFamily.MULTIPART;

		// before the xml check, so that image/svg+xml is an image
		if (mime.startsWith("image/"))
			return MimeFamily.IMAGE;

		if (mime.equals("text/html") || mime.equals("application/xhtml+xml"))
			return MimeFamily.HTML;

		if (mime.equals("application/xml") || mime.equals("text/xml") || mime.endsWith("+xml"))
			return MimeFamily.XML;

		if (JS_TYPES.contains(mime))
			return MimeFamily.JS;

		if (mime.equals("text/css"))
			return MimeFamily.CSS;

		if (mime.startsWith("text/"))
			return MimeFamily.TEXT;

		if (mime.startsWith("audio/"))
			return MimeFamily.AUDIO;

		if (mime.startsWith("video/"))
			return MimeFamily.VIDEO;

		if (mime.equals("application/pdf"))
			return MimeFamily.PDF;

		if (ARCHIVE_TYPES.contains(mime))
			return MimeFamily.ARCHIVE;

		if (mime.equals("application/octet-stream"))
			return MimeFamily.OCTET_STREAM;

		return MimeFamily.OTHER;

	}

}
