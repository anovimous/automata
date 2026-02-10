package com.automata.request.parameter;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class QueryStringParseResult {

	List<QueryParameter> queryParameters = new ArrayList<>();

}
