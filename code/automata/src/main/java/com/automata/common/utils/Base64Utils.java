package com.automata.common.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class Base64Utils {

	public static String decode(String base64) {
		return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
	}

}
