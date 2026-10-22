package com.automata.request.common.dto;

import com.automata.request.common.enums.Method;

// computatedPath is the path with its variable segments abstracted, target the
// request target exactly as it was sent (path and query string)
public record RequestLine(Method method, String computatedPath, String extension, String version, String target) {

}
