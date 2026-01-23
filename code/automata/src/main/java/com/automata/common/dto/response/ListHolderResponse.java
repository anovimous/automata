package com.automata.common.dto.response;

import java.util.List;

import lombok.Getter;

@Getter
public final class ListHolderResponse<T> {

	public ListHolderResponse(List<T> list) {
		this.list = list;
		this.size = list.size();
	}

	private final List<T> list;

	private final int size;

}
