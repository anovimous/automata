package com.automata.tenant.authentication;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaticData {

	@Builder.Default
	private List<AuthNameValuePair> headers = new ArrayList<>();

	@Builder.Default
	private List<AuthNameValuePair> cookies = new ArrayList<>();

	@Builder.Default
	private List<AuthNameValuePair> queryParameters = new ArrayList<>();

}
