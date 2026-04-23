package com.automata.host.common.dto;

import com.automata.request.common.enums.ContentType;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestFilter {
	private Long hostId;
	private Long programId;
	private Long tenantId;
	private Source source;
	private Method method;
	private String computatedPath;
	private String extension;
	private ContentType contentType;
}
