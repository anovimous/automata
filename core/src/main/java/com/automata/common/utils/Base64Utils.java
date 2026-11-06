package com.automata.common.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class Base64Utils {

	public static String decode(String base64) {
		return new String(decodeToBytes(base64), StandardCharsets.UTF_8);
	}

	public static byte[] decodeToBytes(String base64) {
		return Base64.getDecoder().decode(base64);
	}

	public static String encode(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}

}
