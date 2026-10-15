package com.automata.request.equalityset;

import java.util.List;

import com.automata.request.Request;
import com.automata.request.comparator.Modifier;
import com.automata.request.comparator.hash.HashTarget;

public abstract class RequestEqualityUtils {

	public static String computeAlias(Request request, List<Modifier> modifiers) {

		StringBuilder builder = new StringBuilder();

		builder.append(request.getMethod()).append(":").append(request.getComputatedPath());

		if (request.getExtension() != null && !request.getExtension().equals(""))
			builder.append(":").append(request.getExtension());

		modifiers.forEach((mod) -> {
			if (mod.getTarget() == HashTarget.BODY)
				builder.append(":").append(request.getContentType()).append(":")
						.append(request.getNumberOfProperties());
		});

		return builder.toString();

	}

}
