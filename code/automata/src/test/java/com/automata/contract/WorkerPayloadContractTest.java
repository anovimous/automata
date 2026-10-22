package com.automata.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import com.automata.JacksonConfig;
import com.automata.host.common.enums.Scope;
import com.automata.job.api.dto.JobPollDto;
import com.automata.job.api.dto.JobStateUpdateRequest;
import com.automata.job.domain.model.embedded.GenericConfig;
import com.automata.job.domain.model.embedded.MatchAndReplace;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.MatchAndReplaceRule;
import com.automata.job.domain.model.enums.MatchAndReplaceTargetType;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.TargetType;
import com.automata.job.domain.valueobject.HostData;
import com.automata.job.domain.valueobject.HttpJobQueueMetadata;
import com.automata.job.domain.valueobject.JobDetailsFileContainer;
import com.automata.job.domain.valueobject.RequestData;
import com.automata.job.infra.JobDataJsonLinesWriter;
import com.automata.tenant.authentication.AuthNameValuePair;
import com.automata.tenant.authentication.StaticAuthData;
import com.automata.tenant.authentication.StaticData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The JSON which the core sends to the worker, written with the same code and
 * the same Jackson set-up as the application, compared with the samples in
 * src/test/resources/contract. The worker repo keeps a copy of the same samples
 * and parses them with its own types, so a change of a name or a shape fails a
 * test on one side or the other (see docs/worker-contract.md).
 */
@JsonTest
@Import(JacksonConfig.class)
class WorkerPayloadContractTest {

	@Autowired
	ObjectMapper mapper;

	@TempDir
	Path tmp;

	private String sample(String name) throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/contract/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private JsonNode sampleTree(String name) throws IOException {
		return mapper.readTree(sample(name));
	}

	// what is written, read back as a tree, which is how the worker sees it
	private JsonNode written(Object value) throws IOException {
		return mapper.readTree(mapper.writeValueAsString(value));
	}

	private static JobDetailsFileContainer.JobDetailsFileContainerBuilder details() {
		return JobDetailsFileContainer.builder();
	}

	@Test
	void queueMessageIsJobIdAndRoutineKey() throws IOException {
		byte[] body = new Jackson2JsonMessageConverter()
				.toMessage(new HttpJobQueueMetadata(42L, "fuzz"), new MessageProperties()).getBody();

		assertEquals(sampleTree("queue-message.json"), mapper.readTree(body));
	}

	@Test
	void queueMessageIsPersistentJson() {
		MessageProperties properties = new Jackson2JsonMessageConverter()
				.toMessage(new HttpJobQueueMetadata(42L, "fuzz"), new MessageProperties()).getMessageProperties();

		assertEquals("application/json", properties.getContentType());
		assertEquals(MessageDeliveryMode.PERSISTENT, properties.getDeliveryMode());
	}

	@Test
	void detailsFileOfAJobWithAuthentication() throws IOException {
		StaticData data = StaticData.builder().headers(List.of(new AuthNameValuePair("Authorization", "Bearer token")))
				.cookies(List.of(new AuthNameValuePair("session", "abc"))).build();
		StaticAuthData auth = new StaticAuthData(data, Instant.parse("2026-10-20T10:00:00Z"),
				Instant.parse("2026-10-20T09:00:00Z"));
		JobDetailsFileContainer container = details().jobId(42L).routineKey("fuzz").rate(50)
				.verbosity(ResultsVerbosity.NORMAL).wordlistsPaths(new LinkedHashSet<>(List.of("/wordlists/common.txt")))
				.auth(auth).targetType(TargetType.REQUEST)
				.genericConfig(GenericConfig.of(List.of(new MatchAndReplace(MatchAndReplaceTargetType.HEADER,
						MatchAndReplaceRule.ADD, "X-Test", "1"))))
				.customConfig(mapper.readTree(
						"{\"rawRequestBase64\":\"R0VUIC9GVVpaIEhUVFAvMS4x\",\"flags\":[{\"key\":\"-mc\",\"value\":\"200\"}]}"))
				.build();

		assertEquals(sampleTree("details-file.json"), written(container));
	}

	@Test
	void detailsFileOfAJobWithoutAuthenticationHasANullAuth() throws IOException {
		JobDetailsFileContainer container = details().jobId(43L).routineKey("masser").rate(10)
				.verbosity(ResultsVerbosity.SUMMARIZED).wordlistsPaths(new LinkedHashSet<>())
				.targetType(TargetType.HOST).genericConfig(GenericConfig.of(List.of()))
				.customConfig(mapper.readTree("{\"flags\":[]}")).build();

		assertEquals(sampleTree("details-file-without-auth.json"), written(container));
	}

	@Test
	void dataFileLineOfARequest() throws IOException {
		Path file = tmp.resolve("requests.jsonl");
		try (JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(file.toString())) {
			writer.writeRequestData(new RequestData(7L, "R0VUIC8gSFRUUC8xLjENCg=="));
		}

		assertEquals(sample("data-requests.jsonl"), Files.readString(file));
	}

	@Test
	void dataFileLineOfAHost() throws IOException {
		Path file = tmp.resolve("hosts.jsonl");
		try (JobDataJsonLinesWriter writer = new JobDataJsonLinesWriter(file.toString())) {
			writer.writeHostData(new HostData(3L, "example.com", Scope.FQDN));
		}

		assertEquals(sample("data-hosts.jsonl"), Files.readString(file));
	}

	@Test
	void pollAnswerIsTheRequestedState() throws IOException {
		assertEquals(sampleTree("poll-response.json"), written(new JobPollDto(JobState.PAUSED)));
	}

	@Test
	void reportOfTheWorkerIsReadByTheCore() throws IOException {
		JobStateUpdateRequest request = mapper.readValue(sample("update-request.json"), JobStateUpdateRequest.class);

		assertEquals(new JobStateUpdateRequest(42L, JobState.FAILED, "failed to start the tool"), request);
	}

	@Test
	void jobStatesAreTheOnesInTheSample() throws IOException {
		List<String> names = Arrays.stream(JobState.values()).map(Enum::name).toList();

		assertEquals(names, mapper.readValue(sample("job-states.json"), new TypeReference<List<String>>() {
		}));
	}

}
