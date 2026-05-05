package com.automata.job.infra;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import com.automata.job.domain.valueobject.HostData;
import com.automata.job.domain.valueobject.RequestData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

@Getter
public class JobDataJsonLinesWriter {

	public JobDataJsonLinesWriter(String fileLocation) throws IOException {

		this.fileLocation = fileLocation;

		this.writer = new BufferedWriter(new FileWriter(fileLocation, true));

		this.mapper = new ObjectMapper();

	}

	public JobDataJsonLinesWriter(String fileLocation, ObjectMapper mapper) throws IOException {

		this.fileLocation = fileLocation;

		this.writer = new BufferedWriter(new FileWriter(fileLocation, true));

		this.mapper = mapper;

	}

	private final String fileLocation;

	private final BufferedWriter writer;

	private final ObjectMapper mapper;

	public void writeRequestData(RequestData data) throws JsonProcessingException, IOException {

		writer.write(mapper.writeValueAsString(data));

		writer.newLine();

	}

	public void writeHostData(HostData data) throws JsonProcessingException, IOException {

		writer.write(mapper.writeValueAsString(data));

		writer.newLine();

	}
	
	public void flushAndClose() throws IOException {
		writer.flush();
		writer.close();
	}
	
}
