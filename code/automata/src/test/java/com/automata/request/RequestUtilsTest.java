package com.automata.request;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.junit.jupiter.api.Test;

class RequestUtilsTest {

	@Test
	void test() {

		String test = """
				POST /api/v1/users?active=true&role=admin HTTP/1.1
				Host: example.com
				User-Agent: Mozilla/5.0 (X11; Linux x86_64)
				Accept: application/json
				Accept-Language: en-US,en;q=0.9
				Content-Type: application/json; charset=utf-8
				Content-Length: 87
				Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
				X-Forwarded-For: 192.168.1.10
				Connection: keep-alive

				{
				  "username": "anov",
				  "email": "anov@example.com",
				  "enabled": true
				}

								""";

		try {
			RequestUtils.parseRawRequest(test);
		} catch (IOException e) {
			System.out.println("exception");
		} catch (HttpException e) {

			System.out.println("exception");
		}

	}

}
