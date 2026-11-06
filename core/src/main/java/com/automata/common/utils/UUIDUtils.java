package com.automata.common.utils;

import java.util.UUID;

public abstract class UUIDUtils {

	public static boolean isValidUUID(String str) {
		try {
			UUID.fromString(str);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

}
