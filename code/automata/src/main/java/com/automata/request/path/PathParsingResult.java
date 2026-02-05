package com.automata.request.path;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PathParsingResult {

	private String computatedPath;

	private String extension;

	private List<PathVariable> pathVariables = new ArrayList<>();

}
