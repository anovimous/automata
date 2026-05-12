package com.automata.request.common.dto;

import java.util.List;

public record RequestsEqualizationDto(Long hostId, List<Long> requestsIds, Long comparatorId) {

}
