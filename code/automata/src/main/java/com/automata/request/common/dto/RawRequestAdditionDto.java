package com.automata.request.common.dto;

import com.automata.request.common.enums.Source;

public record RawRequestAdditionDto(String requestBase64, Source source, Long hostId, Long tenantId) {

}
