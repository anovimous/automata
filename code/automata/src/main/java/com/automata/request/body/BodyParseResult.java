package com.automata.request.body;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
public class BodyParseResult {

	List<BodyProperty> bodyProperties = new ArrayList<>();

}
