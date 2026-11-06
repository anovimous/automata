package com.automata.common.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Getter;

@Getter
public class PageHolderResponse<T> {

	private final List<T> content;
	private final int page;
	private final int size;
	private final long totalElements;
	private final int totalPages;
	private final boolean last;

	public PageHolderResponse(Page<T> page) {
		this.content = page.getContent();
		this.page = page.getNumber();
		this.size = page.getSize();
		this.totalElements = page.getTotalElements();
		this.totalPages = page.getTotalPages();
		this.last = page.isLast();
	}

}
