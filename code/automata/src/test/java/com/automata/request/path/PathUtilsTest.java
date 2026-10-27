package com.automata.request.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.automata.request.common.dto.PathVariableInternalDto;
import com.automata.request.common.enums.PathVariableValueType;
import com.automata.testsupport.KnownBug;

class PathUtilsTest {

	private static final String UUID_A = "123e4567-e89b-12d3-a456-426614174000";

	// ---- parsePath ----

	@Test
	void aPathWithoutVariablesIsKeptAsIs() {
		PathParsingResult result = PathUtils.parsePath("/api/users/list");

		assertEquals("/api/users/list", result.getComputatedPath());
		assertTrue(result.getPathVariables().isEmpty());
	}

	@Test
	void numericSegmentsBecomeIntVariablesAtTheirIndex() {
		PathParsingResult result = PathUtils.parsePath("/api/users/42/orders/7");

		assertEquals("/api/users/3@INT/orders/5@INT", result.getComputatedPath());
		assertEquals(2, result.getPathVariables().size());
		assertEquals(3, result.getPathVariables().get(0).getIndex());
		assertEquals("42", result.getPathVariables().get(0).getValue());
		assertEquals(PathVariableValueType.INT, result.getPathVariables().get(0).getType());
		assertEquals(5, result.getPathVariables().get(1).getIndex());
		assertEquals("7", result.getPathVariables().get(1).getValue());
	}

	@Test
	void uuidSegmentsBecomeGuidVariables() {
		PathParsingResult result = PathUtils.parsePath("/api/items/" + UUID_A);

		assertEquals("/api/items/3@GUID", result.getComputatedPath());
		assertEquals(PathVariableValueType.GUID, result.getPathVariables().get(0).getType());
		assertEquals(UUID_A, result.getPathVariables().get(0).getValue());
	}

	@Test
	void twoRequestsDifferingOnlyByTheIdsGetTheSameComputedPath() {
		assertEquals(PathUtils.parsePath("/api/users/1/orders/2").getComputatedPath(),
				PathUtils.parsePath("/api/users/987654321/orders/12345").getComputatedPath());
	}

	@Test
	void segmentsThatMerelyContainDigitsAreNotVariables() {
		PathParsingResult result = PathUtils.parsePath("/v2/users/abc123/42a");

		assertEquals("/v2/users/abc123/42a", result.getComputatedPath());
		assertTrue(result.getPathVariables().isEmpty());
	}

	@Test
	void theQueryStringIsNotPartOfTheComputedPath() {
		PathParsingResult result = PathUtils.parsePath("/api/users/5?x=1");

		assertEquals("/api/users/3@INT", result.getComputatedPath());
	}

	@Test
	@KnownBug("BUG-018")
	void theExtensionOfTheLastSegmentIsDetected() {
		assertEquals(".json", PathUtils.parsePath("/api/users.json").getExtension());
		assertEquals(".js", PathUtils.parsePath("/static/app.min.js").getExtension());
	}

	@Test
	void aPathWithoutADotInTheLastSegmentHasNoExtension() {
		assertNull(PathUtils.parsePath("/api/users").getExtension());
	}

	@Test
	@KnownBug("BUG-018")
	void aDotInAnEarlierSegmentIsNotTheExtension() {
		assertNull(PathUtils.parsePath("/api/v1.2/users").getExtension());
	}

	@Test
	@KnownBug("BUG-018")
	void theExtensionOfAFileInASubdirectoryIsDetected() {
		assertEquals(".pdf", PathUtils.parsePath("/files/report.pdf").getExtension());
	}

	@Test
	@KnownBug("BUG-019")
	void theRootPathKeepsItsSlash() {
		assertEquals("/", PathUtils.parsePath("/").getComputatedPath());
	}

	@Test
	@KnownBug("BUG-019")
	void aTrailingSlashIsKept() {
		assertEquals("/api/users/", PathUtils.parsePath("/api/users/").getComputatedPath());
	}

	// ---- validatePath ----

	@ParameterizedTest
	@ValueSource(strings = { "/", "/api", "/api/users/1", "/a%20b", "/api?x=1" })
	void validPaths(String path) {
		assertTrue(PathUtils.validatePath(path).isValid(), path);
	}

	@ParameterizedTest
	@ValueSource(strings = { "api/users", "", "/a b", "?x=1", "//" })
	void invalidPaths(String path) {
		assertFalse(PathUtils.validatePath(path).isValid(), path);
	}

	// ---- composeRawPath ----

	private static PathVariableInternalDto var(long request, int index, String value) {
		return new PathVariableInternalDto(index, value, request);
	}

	@Test
	void composeSubstitutesEveryVariableAtItsIndex() {
		String raw = PathUtils.composeRawPath("/api/users/3@INT/orders/5@INT", List.of(var(1, 3, "42"), var(1, 5, "7")));

		assertEquals("/api/users/42/orders/7", raw);
	}

	@Test
	void composeOfAPathWithoutVariablesIsTheSamePath() {
		assertEquals("/api/users", PathUtils.composeRawPath("/api/users", List.of()));
	}

	@Test
	void parseThenComposeGivesBackTheOriginalPath() {
		String original = "/api/users/42/items/" + UUID_A + "/details";
		PathParsingResult parsed = PathUtils.parsePath(original);

		List<PathVariableInternalDto> dtos = parsed.getPathVariables().stream()
				.map(v -> new PathVariableInternalDto(v.getIndex(), v.getValue(), 1L)).toList();

		assertEquals(original, PathUtils.composeRawPath(parsed.getComputatedPath(), dtos));
	}

	@Test
	@KnownBug("BUG-019")
	void parseThenComposeKeepsTheTrailingSlashAndTheRoot() {
		for (String original : List.of("/", "/api/users/", "/api/1/")) {
			PathParsingResult parsed = PathUtils.parsePath(original);
			List<PathVariableInternalDto> dtos = parsed.getPathVariables().stream()
					.map(v -> new PathVariableInternalDto(v.getIndex(), v.getValue(), 1L)).toList();

			assertEquals(original, PathUtils.composeRawPath(parsed.getComputatedPath(), dtos), original);
		}
	}

	@Test
	@KnownBug("BUG-020")
	void aLiteralSegmentThatLooksLikeAPlaceholderSurvivesTheRoundTrip() {
		String original = "/docs/1@INT";
		PathParsingResult parsed = PathUtils.parsePath(original);

		assertEquals(original, PathUtils.composeRawPath(parsed.getComputatedPath(), List.of()));
	}
}
