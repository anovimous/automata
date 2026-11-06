package com.automata.job.service;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.automata.job.domain.exception.InvalidCustomConfigException;
import com.automata.routine.Routine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

@Service
public class CustomConfigValidator {

	private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

	// A routine without a config schema is not validated.
	public void validate(Routine routine, JsonNode customConfig) {

		JsonNode configSchema = routine.getConfigSchema();

		if (configSchema == null || configSchema.isNull())
			return;

		Set<ValidationMessage> violations;

		try {
			JsonSchema schema = schemaFactory.getSchema(configSchema);
			violations = schema.validate(customConfig != null ? customConfig : NullNode.getInstance());
		} catch (JsonSchemaException e) {
			throw new IllegalStateException(
					"The config schema of routine '" + routine.getKey() + "' is not a valid JSON Schema", e);
		}

		if (violations.isEmpty())
			return;

		Set<String> messages = new TreeSet<>();
		violations.forEach(violation -> messages.add(violation.getMessage()));

		throw new InvalidCustomConfigException(routine.getKey(), messages);

	}

}
