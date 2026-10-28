package com.automata.e2e;

import static com.automata.e2e.RawHttp.delete;
import static com.automata.e2e.RawHttp.get;
import static com.automata.e2e.RawHttp.head;
import static com.automata.e2e.RawHttp.patch;
import static com.automata.e2e.RawHttp.post;
import static com.automata.e2e.RawHttp.put;
import static com.automata.request.common.enums.RequestContentType.FORM;
import static com.automata.request.common.enums.RequestContentType.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.automata.common.dto.BodyStorage;
import com.automata.common.dto.HeaderEntry;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.RequestContentType;

/**
 * A corpus of raw requests, each sent through the HTTP layer into the database and read back through every route.
 * Each case states what must be stored and what the views must show; the bytes that were sent must always come
 * back unchanged.
 */
@RequestE2E
class RequestCorpusE2ETest extends RequestE2ETestBase {

	record Case(String name, RawHttp raw, Expected expected, List<HeaderEntry> headers) {

		@Override
		public String toString() {
			return name;
		}
	}

	private static Arguments c(String name, RawHttp raw, Expected expected) {
		return Arguments.of(new Case(name, raw, expected, null));
	}

	private static Arguments c(String name, RawHttp raw, Expected expected, List<HeaderEntry> headers) {
		return Arguments.of(new Case(name, raw, expected, headers));
	}

	private void verify(Case c) throws Exception {
		ingestAndVerify(c.name(), c.raw(), c.expected(), c.headers());
	}

	private static String repeat(String text, int times) {
		return text.repeat(times);
	}

	// ============================================================================================ methods, versions

	static Stream<Arguments> methodsAndVersions() {
		List<Arguments> cases = new ArrayList<>();
		for (Method method : Method.values())
			cases.add(c("method " + method, RawHttp.of(method.name(), "/a"), Expected.path("/a").method(method)));
		cases.add(c("lowercase method", RawHttp.of("get", "/a"), Expected.path("/a")));
		cases.add(c("mixed case method", RawHttp.of("PoSt", "/a"), Expected.path("/a").method(Method.POST)));
		cases.add(c("lowercase put", RawHttp.of("put", "/a"), Expected.path("/a").method(Method.PUT)));
		cases.add(c("HTTP/2", get("/a").version("HTTP/2"), Expected.path("/a").version("HTTP/2")));
		cases.add(c("HTTP/2.0", get("/a").version("HTTP/2.0"), Expected.path("/a").version("HTTP/2")));
		cases.add(c("HTTP/2 post with json", post("/a").version("HTTP/2").json("{\"a\":1}"),
				Expected.path("/a").method(Method.POST).version("HTTP/2").inline(JSON).props("a=1:INT")));
		cases.add(c("HEAD without body", head("/a"), Expected.path("/a").method(Method.HEAD)));
		cases.add(c("DELETE with a json body", delete("/a/7").json("{\"force\":true}"),
				Expected.path("/a/2@INT").method(Method.DELETE).vars("2:INT=7").inline(JSON).props("force=true:BOOLEAN")));
		cases.add(c("GET with a json body", get("/a").json("{\"a\":1}"), Expected.path("/a").inline(JSON).props("a=1:INT")));
		cases.add(c("PATCH with json patch", patch("/a/1").contentType("application/json-patch+json").body("[{\"op\":\"add\",\"path\":\"/x\"}]"),
				Expected.path("/a/2@INT").method(Method.PATCH).vars("2:INT=1").inline(JSON).props("[0].op=add:STRING", "[0].path=/x:STRING")));
		return cases.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("methodsAndVersions")
	void methodsAndVersions(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ framing

	static Stream<Arguments> framing() {
		return Stream.of(
				c("crlf", get("/a"), Expected.path("/a")),
				c("bare lf", get("/a").eol("\n"), Expected.path("/a")),
				c("bare lf with json body", post("/a").eol("\n").json("{\"a\":1}"), Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT")),
				c("no terminating empty line", get("/a").unterminated(), Expected.path("/a")),
				c("no terminating empty line, bare lf", get("/a").eol("\n").unterminated(), Expected.path("/a")),
				c("no headers", get("/a").noHost(), Expected.path("/a")),
				c("no headers and no terminator", get("/a").noHost().unterminated(), Expected.path("/a")),
				c("no Host header", get("/a").noHost().header("Accept", "*/*"), Expected.path("/a")),
				c("a body that contains empty lines", post("/a").contentType("text/plain").body("line1\r\n\r\nline3\n\nline5"),
						Expected.path("/a").method(Method.POST).inline(RequestContentType.TEXT)),
				c("a body that ends with crlf", post("/a").form("a=1&b=2\r\n"),
						Expected.path("/a").method(Method.POST).inline(FORM).props("a=1:STRING", "b=2:STRING")),
				c("a content-length that is wrong", post("/a").header("Content-Length", "3").json("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT")),
				c("a content-length of zero with a body", post("/a").header("Content-Length", "0").json("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT")),
				c("expect 100-continue", post("/a").header("Expect", "100-continue").contentType("text/plain").body("abc"),
						Expected.path("/a").method(Method.POST).inline(RequestContentType.TEXT)),
				c("a content type without a body", post("/a").contentType("application/json"), Expected.path("/a").method(Method.POST)),
				c("a body without a content type", post("/a").body("a=1"), Expected.path("/a").method(Method.POST).storage(BodyStorage.INLINE)),
				c("tabs between the tokens of the request line", get("/a").requestLine("GET\t/a\tHTTP/1.1"), Expected.path("/a")),
				c("several spaces between the tokens of the request line", get("/a").requestLine("GET   /a   HTTP/1.1"), Expected.path("/a")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("framing")
	void framing(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ targets and paths

	static Stream<Arguments> paths() {
		String uuid = "550e8400-e29b-41d4-a716-446655440000";
		return Stream.of(
				c("a simple path", get("/api/users"), Expected.path("/api/users")),
				c("a deep path", get("/a/b/c/d/e/f/g/h"), Expected.path("/a/b/c/d/e/f/g/h")),
				c("one numeric segment", get("/users/42"), Expected.path("/users/2@INT").vars("2:INT=42")),
				c("two numeric segments", get("/users/42/orders/7"), Expected.path("/users/2@INT/orders/4@INT").vars("2:INT=42", "4:INT=7")),
				c("a numeric segment first", get("/42/users"), Expected.path("/1@INT/users").vars("1:INT=42")),
				c("a numeric segment last", get("/users/0"), Expected.path("/users/2@INT").vars("2:INT=0")),
				c("zeros in front of a number", get("/users/007"), Expected.path("/users/2@INT").vars("2:INT=007")),
				c("a very long number", get("/users/99999999999999999999"), Expected.path("/users/2@INT").vars("2:INT=99999999999999999999")),
				c("a lower case uuid", get("/users/" + uuid), Expected.path("/users/2@GUID").vars("2:GUID=" + uuid)),
				c("an upper case uuid", get("/users/" + uuid.toUpperCase()), Expected.path("/users/2@GUID").vars("2:GUID=" + uuid.toUpperCase())),
				c("a uuid and a number", get("/u/" + uuid + "/o/12"), Expected.path("/u/2@GUID/o/4@INT").vars("2:GUID=" + uuid, "4:INT=12")),
				c("a uuid without dashes is a plain segment", get("/users/550e8400e29b41d4a716446655440000"),
						Expected.path("/users/550e8400e29b41d4a716446655440000")),
				c("a number inside a word is a plain segment", get("/v2/users/abc123/42a/a42"), Expected.path("/v2/users/abc123/42a/a42")),
				c("a negative number is a plain segment", get("/a/-5/b"), Expected.path("/a/-5/b")),
				c("a segment of plus signs and dashes", get("/a/+5/b-c"), Expected.path("/a/+5/b-c")),
				c("a percent encoded space", get("/my%20files/report"), Expected.path("/my%20files/report")),
				c("a percent encoded slash", get("/a%2Fb/c"), Expected.path("/a%2Fb/c")),
				c("a percent encoded percent sign", get("/discount/100%25"), Expected.path("/discount/100%25")),
				c("a percent encoded question mark", get("/a%3Fb/c?x=1"), Expected.path("/a%3Fb/c").query("x=1")),
				c("a percent encoded hash", get("/a%23b"), Expected.path("/a%23b")),
				c("a percent encoded nul", get("/a%00b"), Expected.path("/a%00b")),
				c("a percent encoded unicode", get("/caf%C3%A9/%E2%82%AC"), Expected.path("/caf%C3%A9/%E2%82%AC")),
				c("percent encoded dot segments", get("/a/%2e%2e/b"), Expected.path("/a/%2e%2e/b")),
				c("an encoded number is a plain segment", get("/users/%34%32"), Expected.path("/users/%34%32")),
				c("a lone percent sign", get("/discount/100%"), Expected.path("/discount/100%")),
				c("a malformed escape", get("/a/%zz/b"), Expected.path("/a/%zz/b")),
				c("square brackets", get("/a[b]/c"), Expected.path("/a[b]/c")),
				c("curly braces", get("/a/{id}/c"), Expected.path("/a/{id}/c")),
				c("a bracket around a number is a plain segment", get("/a/[1]/{2}"), Expected.path("/a/[1]/{2}")),
				c("pipes and carets", get("/a|b/c^d"), Expected.path("/a|b/c^d")),
				c("quotes and angle brackets", get("/a\"b/c<d>e"), Expected.path("/a\"b/c<d>e")),
				c("a backslash and a backtick", get("/a\\b/c`d"), Expected.path("/a\\b/c`d")),
				c("a semicolon path parameter", get("/a;jsessionid=1/b"), Expected.path("/a;jsessionid=1/b")),
				c("an at sign", get("/users/@me/x@y"), Expected.path("/users/@me/x@y")),
				c("a colon", get("/a:b/c"), Expected.path("/a:b/c")),
				c("a tilde and underscores", get("/~user/a_b"), Expected.path("/~user/a_b")),
				c("a path of 499 characters", get("/" + repeat("a", 499)), Expected.path("/" + repeat("a", 499))),
				c("an absolute url with a path", get("http://h:81/a/1?x=1"), Expected.path("/a/2@INT").vars("2:INT=1").query("x=1")),
				c("an https absolute url", get("https://api.example.com:8443/a/b"), Expected.path("/a/b")),
				c("an absolute url with user info", get("http://user:pw@h/a"), Expected.path("/a")),
				c("an absolute url with an ipv6 host", get("http://[::1]:8080/a"), Expected.path("/a")),
				c("a fragment is not part of the path", get("/a/b#section"), Expected.path("/a/b")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("paths")
	void paths(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ query strings

	static Stream<Arguments> queryStrings() {
		return Stream.of(
				c("no query", get("/a"), Expected.path("/a")),
				c("an empty query", get("/a?"), Expected.path("/a")),
				c("one parameter", get("/a?x=1"), Expected.path("/a").query("x=1")),
				c("several parameters in order", get("/a?z=1&a=2&m=3"), Expected.path("/a").query("z=1", "a=2", "m=3")),
				c("a repeated parameter", get("/a?id=1&id=2&id=3"), Expected.path("/a").query("id=1", "id=2", "id=3")),
				c("a parameter without a value", get("/a?debug"), Expected.path("/a").query("debug=")),
				c("parameters with and without a value", get("/a?flag&x=1&other&y="), Expected.path("/a").query("flag=", "x=1", "other=", "y=")),
				c("an empty value", get("/a?x="), Expected.path("/a").query("x=")),
				c("an empty parameter name is dropped", get("/a?=1&b=2"), Expected.path("/a").query("b=2")),
				c("empty pairs are dropped", get("/a?a=1&&b=2&"), Expected.path("/a").query("a=1", "b=2")),
				c("a value with equals signs", get("/a?token=abc==&x=a=b"), Expected.path("/a").query("token=abc==", "x=a=b")),
				c("a plus is a space", get("/a?q=a+b"), Expected.path("/a").query("q=a b")),
				c("an encoded plus is a plus", get("/a?q=a%2Bb"), Expected.path("/a").query("q=a+b")),
				c("an encoded space", get("/a?q=a%20b"), Expected.path("/a").query("q=a b")),
				c("an encoded ampersand does not split", get("/a?q=a%26b&page=2"), Expected.path("/a").query("q=a&b", "page=2")),
				c("an encoded equals sign", get("/a?token=abc%3D%3D"), Expected.path("/a").query("token=abc==")),
				c("an encoded hash and question mark", get("/a?q=%23x%3Fy"), Expected.path("/a").query("q=#x?y")),
				c("an encoded slash", get("/a?next=%2Fhome%2Fuser"), Expected.path("/a").query("next=/home/user")),
				c("an encoded percent sign", get("/a?p=100%25"), Expected.path("/a").query("p=100%")),
				c("an encoded parameter name", get("/a?na%20me=v&%5B%5D=1"), Expected.path("/a").query("na me=v", "[]=1")),
				c("encoded unicode", get("/a?q=%C3%A9%E2%82%AC%F0%9F%98%80"), Expected.path("/a").query("q=é€😀")),
				c("a lone percent sign", get("/a?p=50%&q=1"), Expected.path("/a").query("p=50%", "q=1")),
				c("a malformed escape", get("/a?b=%zz&c=%4"), Expected.path("/a").query("b=%zz", "c=%4")),
				c("unencoded square brackets", get("/api?filter[name]=bob&sort[]=id"), Expected.path("/api").query("filter[name]=bob", "sort[]=id")),
				c("unencoded curly braces", get("/api?ids={1}&q={\"a\":1}"), Expected.path("/api").query("ids={1}", "q={\"a\":1}")),
				c("unencoded pipes and carets", get("/api?ids=1|2|3&x=a^b"), Expected.path("/api").query("ids=1|2|3", "x=a^b")),
				c("unencoded quotes, angle brackets and backslashes", get("/api?q=\"a\"<b>&p=c\\d"), Expected.path("/api").query("q=\"a\"<b>", "p=c\\d")),
				c("a semicolon is not a separator", get("/a?a=1;b=2"), Expected.path("/a").query("a=1;b=2")),
				c("a question mark inside the query", get("/a?next=/b?c=1"), Expected.path("/a").query("next=/b?c=1")),
				c("a fragment is cut", get("/a?x=1#top"), Expected.path("/a").query("x=1")),
				c("a query on the numeric path", get("/users/42?active=true&page=2"),
						Expected.path("/users/2@INT").vars("2:INT=42").query("active=true", "page=2")),
				c("a value of 2000 characters", get("/a?jwt=" + repeat("e", 2000)), Expected.path("/a").query("jwt=" + repeat("e", 2000))),
				c("a parameter name of 500 characters", get("/a?" + repeat("n", 500) + "=1"), Expected.path("/a").query(repeat("n", 500) + "=1")),
				c("two hundred parameters", get("/a?" + String.join("&", IntStream.range(0, 200).mapToObj(i -> "k" + i + "=v" + i).toList())),
						Expected.path("/a").query(IntStream.range(0, 200).mapToObj(i -> "k" + i + "=v" + i).toArray(String[]::new))),
				c("a json document in the query", get("/a?filter=%7B%22a%22%3A%5B1%2C2%5D%7D"), Expected.path("/a").query("filter={\"a\":[1,2]}")),
				c("an absolute url with a query", get("http://h/a?x=1&y=2"), Expected.path("/a").query("x=1", "y=2")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("queryStrings")
	void queryStrings(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ headers

	static Stream<Arguments> headers() {
		String longValue = repeat("v", 9000);
		return Stream.of(
				c("common headers", get("/a").header("Accept", "application/json").header("User-Agent", "Mozilla/5.0").header("Accept-Language", "en-US,en;q=0.9"),
						Expected.path("/a")),
				c("cookies and authorization", get("/a").header("Cookie", "a=1; b=2; session=abc.def").header("Authorization", "Bearer abc.def.ghi"),
						Expected.path("/a")),
				c("header names in every case", get("/a").header("x-lower", "1").header("X-UPPER", "2").header("X-Mixed-Case", "3"), Expected.path("/a")),
				c("headers with digits and underscores", get("/a").header("X-Api-Key-2", "k").header("X_Under_Score", "u"), Expected.path("/a")),
				c("a repeated header", get("/a").header("X-A", "1").header("X-A", "2"), Expected.path("/a")),
				c("a header repeated in another case", get("/a").header("X-A", "1").header("x-a", "2"), Expected.path("/a")),
				c("an empty header value", get("/a").header("X-Empty", ""), Expected.path("/a")),
				c("a header value with colons", get("/a").header("Referer", "https://example.com:8443/path?x=1"), Expected.path("/a")),
				c("a header value with leading and trailing spaces", get("/a").rawHeaderLine("X-Spaces:    padded   "), Expected.path("/a"),
						List.of(new HeaderEntry("Host", RawHttp.HOST), new HeaderEntry("X-Spaces", "padded"))),
				c("a folded header", get("/a").rawHeaderLine("X-Folded: one").rawHeaderLine("  two").rawHeaderLine("\tthree"), Expected.path("/a"),
						List.of(new HeaderEntry("Host", RawHttp.HOST), new HeaderEntry("X-Folded", "one two three"))),
				c("a very long header value", get("/a").header("X-Long", longValue), Expected.path("/a")),
				c("two hundred headers", withHeaders(get("/a"), 200), Expected.path("/a")),
				c("a header name of 2000 characters", get("/a").header(repeat("h", 2000), "1"), Expected.path("/a")),
				c("the same header names twice", get("/a").header("Accept", "*/*").header("Accept", "text/html"), Expected.path("/a")),
				c("content-type in lower case", post("/a").header("content-type", "application/json").body("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT")),
				c("content-type in upper case", post("/a").header("CONTENT-TYPE", "APPLICATION/JSON").body("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT")),
				c("content-type with parameters", post("/a").header("Content-Type", "application/json ;charset=UTF-8; x=y").body("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT")),
				c("two content-type headers, the first one counts", post("/a").contentType("text/plain").contentType("application/json").body("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(RequestContentType.TEXT)));
	}

	private static RawHttp withHeaders(RawHttp raw, int count) {
		for (int i = 0; i < count; i++)
			raw.header("X-Header-" + i, "value-" + i);
		return raw;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("headers")
	void headers(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ json bodies

	private static Arguments j(String name, String body, String... props) {
		return Arguments.of(new Case(name, post("/a").json(body), Expected.path("/a").method(Method.POST).inline(JSON).props(props), null));
	}

	static Stream<Arguments> jsonBodies() {
		return Stream.of(
				j("flat scalars", "{\"s\":\"x\",\"i\":1,\"d\":1.5,\"b\":true,\"f\":false,\"n\":null}", "s=x:STRING", "i=1:INT", "d=1.5:DOUBLE",
						"b=true:BOOLEAN", "f=false:BOOLEAN", "n=null:NULL"),
				j("nested objects", "{\"a\":{\"b\":{\"c\":1}}}", "a.b.c=1:INT"),
				j("an array of scalars", "{\"a\":[1,\"x\",true,null]}", "a[0]=1:INT", "a[1]=x:STRING", "a[2]=true:BOOLEAN", "a[3]=null:NULL"),
				j("an array of objects", "{\"users\":[{\"id\":1,\"n\":\"a\"},{\"id\":2,\"n\":\"b\"}]}", "users[0].id=1:INT", "users[0].n=a:STRING",
						"users[1].id=2:INT", "users[1].n=b:STRING"),
				j("nested arrays", "{\"m\":[[1,2],[3]]}", "m[0][0]=1:INT", "m[0][1]=2:INT", "m[1][0]=3:INT"),
				j("a root array", "[1,{\"a\":2},[3]]", "[0]=1:INT", "[1].a=2:INT", "[2][0]=3:INT"),
				j("a root array of objects", "[{\"a\":1},{\"a\":2}]", "[0].a=1:INT", "[1].a=2:INT"),
				j("empty containers inside", "{\"o\":{},\"a\":[]}", "o=null:EMPTY_OBJECT", "a=null:EMPTY_ARRAY"),
				j("an empty root object", "{}"),
				j("an empty root array", "[]", "root=null:EMPTY_ARRAY"),
				j("a root number", "42", "root=42:INT"),
				j("a root string", "\"hi\"", "root=hi:STRING"),
				j("a root boolean", "true", "root=true:BOOLEAN"),
				j("a root null", "null", "root=null:NULL"),
				j("a negative number and a negative decimal", "{\"n\":-5,\"m\":-1.5}", "n=-5:INT", "m=-1.5:DOUBLE"),
				j("a long", "{\"n\":9999999999,\"m\":-9999999999}", "n=9999999999:LONG", "m=-9999999999:LONG"),
				j("the largest int and one more", "{\"a\":2147483647,\"b\":2147483648}", "a=2147483647:INT", "b=2147483648:LONG"),
				j("the largest long", "{\"a\":9223372036854775807}", "a=9223372036854775807:LONG"),
				j("zero and decimal zero", "{\"a\":0,\"b\":0.0}", "a=0:INT", "b=0.0:DOUBLE"),
				j("a key with a dot", "{\"a.b\":1}", "[\"a.b\"]=1:INT"),
				j("a key that looks like an index", "{\"c[0]\":2}", "[\"c[0]\"]=2:INT"),
				j("a key with spaces and symbols", "{\"first name\":\"a\",\"$ref\":\"b\",\"@id\":\"c\"}", "first name=a:STRING", "$ref=b:STRING",
						"@id=c:STRING"),
				j("an empty key", "{\"\":1}", "[\"\"]=1:INT"),
				j("unicode text", "{\"n\":\"café € 😀\"}", "n=café € 😀:STRING"),
				j("escapes in strings", "{\"a\":\"line\\nbreak \\\"q\\\" tab\\t \\u00e9 \\ud83d\\ude00 back\\\\slash\"}",
						"a=line\nbreak \"q\" tab\t é 😀 back\\slash:STRING"),
				j("an empty string", "{\"a\":\"\"}", "a=:STRING"),
				j("a string that looks like a number", "{\"a\":\"123\",\"b\":\"true\",\"c\":\"null\"}", "a=123:STRING", "b=true:STRING", "c=null:STRING"),
				j("a duplicate key keeps the last value", "{\"a\":1,\"a\":2}", "a=2:INT"),
				j("whitespace around the document", " \r\n\t{\"a\":1}\r\n ", "a=1:INT"),
				j("a pretty printed document", "{\n  \"a\": {\n    \"b\": [\n      1,\n      2\n    ]\n  }\n}", "a.b[0]=1:INT", "a.b[1]=2:INT"),
				j("fifty levels of nesting", repeat("[", 50) + "1" + repeat("]", 50), repeat("[0]", 50) + "=1:INT"),
				j("a string of 200000 characters", "{\"a\":\"" + repeat("x", 200_000) + "\"}", "a=" + repeat("x", 200_000) + ":STRING"),
				j("a thousand array elements", thousandElements(), thousandProps()),
				Arguments.of(new Case("a vendor json content type", post("/a").contentType("application/vnd.api+json").body("{\"a\":1}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("a=1:INT"), null)),
				Arguments.of(new Case("json with a charset", post("/a").contentType("application/json; charset=utf-8").body("{\"n\":\"é\"}"),
						Expected.path("/a").method(Method.POST).inline(JSON).props("n=é:STRING"), null)));
	}

	private static String thousandElements() {
		return "{\"v\":[" + String.join(",", IntStream.range(0, 1000).mapToObj(Integer::toString).toList()) + "]}";
	}

	private static String[] thousandProps() {
		return IntStream.range(0, 1000).mapToObj(i -> "v[" + i + "]=" + i + ":INT").toArray(String[]::new);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("jsonBodies")
	void jsonBodies(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ form bodies

	private static Arguments f(String name, String body, String... props) {
		return Arguments.of(new Case(name, post("/a").form(body), Expected.path("/a").method(Method.POST).inline(FORM).props(props), null));
	}

	static Stream<Arguments> formBodies() {
		return Stream.of(
				f("simple fields", "user=bob&pass=secret", "user=bob:STRING", "pass=secret:STRING"),
				f("an empty value", "a=&b=2", "a=:STRING", "b=2:STRING"),
				f("a field without a value", "a=1&flag&b=2", "a=1:STRING", "flag=null:STRING", "b=2:STRING"),
				f("a repeated field", "id=1&id=2", "id=1:STRING", "id=2:STRING"),
				f("plus and encoded characters", "q=a+b%20c&p=x%26y%3Dz", "q=a b c:STRING", "p=x&y=z:STRING"),
				f("encoded unicode", "n=%C3%A9%E2%82%AC", "n=é€:STRING"),
				f("brackets in the names", "filter[name]=x&ids[]=1&ids[]=2", "filter[name]=x:STRING", "ids[]=1:STRING", "ids[]=2:STRING"),
				f("dots in the names", "a.b=1&c.d.e=2", "a.b=1:STRING", "c.d.e=2:STRING"),
				f("a semicolon is not a separator", "a=1;b=2", "a=1;b=2:STRING"),
				f("an equals sign in the value", "token=abc==", "token=abc==:STRING"),
				f("empty names are dropped", "=v&=w&a=1", "a=1:STRING"),
				f("empty pairs are dropped", "&&a=1&&", "a=1:STRING"),
				f("a lone percent sign", "p=50%&q=1", "p=50%:STRING", "q=1:STRING"),
				f("a malformed escape", "b=%zz", "b=%zz:STRING"),
				f("a long value", "d=" + repeat("v", 100_000), "d=" + repeat("v", 100_000) + ":STRING"),
				f("a name of 2000 characters", repeat("k", 2000) + "=1", repeat("k", 2000) + "=1:STRING"),
				Arguments.of(new Case("form with a charset", post("/a").contentType("application/x-www-form-urlencoded; charset=UTF-8").body("a=1"),
						Expected.path("/a").method(Method.POST).inline(FORM).props("a=1:STRING"), null)),
				Arguments.of(new Case("an empty form body", post("/a").contentType("application/x-www-form-urlencoded"),
						Expected.path("/a").method(Method.POST), null)),
				Arguments.of(new Case("a form body of only separators", post("/a").form("&&"),
						Expected.path("/a").method(Method.POST).inline(FORM), null)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("formBodies")
	void formBodies(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ other bodies

	private static byte[] allBytes() {
		byte[] bytes = new byte[256 * 4];
		for (int i = 0; i < bytes.length; i++)
			bytes[i] = (byte) i;
		return bytes;
	}

	private static Arguments other(String name, RawHttp raw, RequestContentType type) {
		return Arguments.of(new Case(name, raw, Expected.path(raw.target()).method(Method.POST).inline(type), null));
	}

	static Stream<Arguments> otherBodies() {
		String multipart = "--XX\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\n1\r\n--XX\r\nContent-Disposition: form-data; name=\"f\"; filename=\"x.bin\"\r\n"
				+ "Content-Type: application/octet-stream\r\n\r\n\u0000\u0001ÿ\r\n--XX--\r\n";
		return Stream.of(
				Arguments.of(new Case("multipart is stored as a file", post("/upload").contentType("multipart/form-data; boundary=XX").body(multipart),
						Expected.path("/upload").method(Method.POST).contentType(RequestContentType.MULTIPART).storage(BodyStorage.FILE), null)),
				Arguments.of(new Case("multipart with binary content", post("/upload").contentType("multipart/form-data; boundary=XX").body(allBytes()),
						Expected.path("/upload").method(Method.POST).contentType(RequestContentType.MULTIPART).storage(BodyStorage.FILE), null)),
				Arguments.of(new Case("multipart mixed", post("/upload").contentType("multipart/mixed; boundary=B").body("--B--"),
						Expected.path("/upload").method(Method.POST).contentType(RequestContentType.MULTIPART).storage(BodyStorage.FILE), null)),
				other("an xml body", post("/a").contentType("text/xml").body("<a><b>1</b></a>"), RequestContentType.XML),
				other("an application/xml body", post("/a").contentType("application/xml; charset=utf-8").body("<a/>"), RequestContentType.XML),
				other("a soap body", post("/a").contentType("application/soap+xml").body("<s/>"), RequestContentType.XML),
				other("a text body", post("/a").contentType("text/plain").body("hello\r\nworld"), RequestContentType.TEXT),
				other("a graphql text body", post("/graphql").contentType("application/graphql").body("{ user(id: 1) { name } }"), RequestContentType.OTHER),
				other("an html body", post("/a").contentType("text/html").body("<html></html>"), RequestContentType.HTML),
				other("a javascript body", post("/a").contentType("application/javascript").body("var a = 1;"), RequestContentType.JS),
				other("a css body", post("/a").contentType("text/css").body("a{}"), RequestContentType.CSS),
				other("a pdf body", post("/a").contentType("application/pdf").body("%PDF-1.4"), RequestContentType.PDF),
				other("an image body", post("/a").contentType("image/png").body(allBytes()), RequestContentType.IMAGE),
				other("an svg is an image", post("/a").contentType("image/svg+xml").body("<svg/>"), RequestContentType.IMAGE),
				other("a zip body", post("/a").contentType("application/zip").body(allBytes()), RequestContentType.ARCHIVE),
				other("an audio body", post("/a").contentType("audio/mpeg").body(allBytes()), RequestContentType.AUDIO),
				other("a video body", post("/a").contentType("video/mp4").body(allBytes()), RequestContentType.VIDEO),
				other("an octet stream with every byte value", post("/a").contentType("application/octet-stream").body(allBytes()),
						RequestContentType.OCTET_STREAM),
				other("an unknown content type", post("/a").contentType("application/x-custom").body("data"), RequestContentType.OTHER),
				other("a binary body that contains a blank line",
						post("/a").contentType("application/octet-stream").body(new byte[] { 1, 2, '\r', '\n', '\r', '\n', 3, 4, 0, (byte) 0xff }),
						RequestContentType.OCTET_STREAM),
				other("a body of one megabyte", post("/a").contentType("application/octet-stream").body(new byte[1_000_000]),
						RequestContentType.OCTET_STREAM),
				other("a text body with the nul character", post("/a").contentType("text/plain").body("a\u0000b"), RequestContentType.TEXT));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("otherBodies")
	void otherBodies(Case c) throws Exception {
		verify(c);
	}

	// ============================================================================================ realistic requests

	static Stream<Arguments> realisticRequests() {
		String uuid = "6f1c0a52-0000-4000-8000-000000000001";
		return Stream.of(
				c("a login",
						post("/api/v1/auth/login").header("Accept", "application/json").header("Origin", "https://app.example.com")
								.header("Referer", "https://app.example.com/login").json("{\"email\":\"bob@example.com\",\"password\":\"p@ss w0rd!\",\"remember\":true}"),
						Expected.path("/api/v1/auth/login").method(Method.POST).inline(JSON).props("email=bob@example.com:STRING",
								"password=p@ss w0rd!:STRING", "remember=true:BOOLEAN")),
				c("a rest update",
						put("/api/v1/users/42/profile").header("Authorization", "Bearer eyJhbGciOi.eyJzdWIi.sig").header("If-Match", "\"etag-1\"")
								.json("{\"name\":\"Bob\",\"address\":{\"street\":\"1 Main St\",\"zip\":\"01234\"},\"tags\":[\"a\",\"b\"]}"),
						Expected.path("/api/v1/users/4@INT/profile").method(Method.PUT).vars("4:INT=42").inline(JSON).props("name=Bob:STRING",
								"address.street=1 Main St:STRING", "address.zip=01234:STRING", "tags[0]=a:STRING", "tags[1]=b:STRING")),
				c("a search with filters",
						get("/api/search?filter[status]=open&filter[owner]=" + uuid + "&sort=-created&page[number]=2&page[size]=50").header("Accept", "application/vnd.api+json"),
						Expected.path("/api/search").query("filter[status]=open", "filter[owner]=" + uuid, "sort=-created", "page[number]=2", "page[size]=50")),
				c("a graphql query",
						post("/graphql").json("{\"operationName\":\"Q\",\"query\":\"query Q($id: ID!) { user(id: $id) { name friends { id } } }\",\"variables\":{\"id\":\"1\"}}"),
						Expected.path("/graphql").method(Method.POST).inline(JSON).props("operationName=Q:STRING",
								"query=query Q($id: ID!) { user(id: $id) { name friends { id } } }:STRING", "variables.id=1:STRING")),
				c("an oauth token request",
						post("/oauth/token").header("Authorization", "Basic Y2xpZW50OnNlY3JldA==")
								.form("grant_type=authorization_code&code=abc%2Fdef%3D&redirect_uri=https%3A%2F%2Fapp.example.com%2Fcb&client_id=c1"),
						Expected.path("/oauth/token").method(Method.POST).inline(FORM).props("grant_type=authorization_code:STRING", "code=abc/def=:STRING",
								"redirect_uri=https://app.example.com/cb:STRING", "client_id=c1:STRING")),
				c("a file upload",
						post("/api/files/9").contentType("multipart/form-data; boundary=----WebKitFormBoundaryX")
								.body("------WebKitFormBoundaryX\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\nhello\r\n------WebKitFormBoundaryX--\r\n"),
						Expected.path("/api/files/3@INT").method(Method.POST).vars("3:INT=9").contentType(RequestContentType.MULTIPART)
								.storage(BodyStorage.FILE)),
				c("a delete with an id and a uuid", delete("/api/orgs/" + uuid + "/members/12").header("X-Request-Id", uuid),
						Expected.path("/api/orgs/3@GUID/members/5@INT").method(Method.DELETE).vars("3:GUID=" + uuid, "5:INT=12")),
				c("a cors style request",
						get("/api/v1/items?limit=10").header("Origin", "https://app.example.com").header("Access-Control-Request-Method", "GET").header("Sec-Fetch-Mode", "cors"),
						Expected.path("/api/v1/items").query("limit=10")),
				c("a proxy style absolute url request", get("http://api.example.com/api/v1/items/5?expand=owner").header("Proxy-Connection", "keep-alive"),
						Expected.path("/api/v1/items/4@INT").vars("4:INT=5").query("expand=owner")));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("realisticRequests")
	void realisticRequests(Case c) throws Exception {
		verify(c);
	}

}
