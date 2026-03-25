package com.automata;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class RustfsConfig {

	@Bean
	S3Client s3Client() {
		return S3Client.builder().endpointOverride(URI.create("http://localhost:9000")).region(Region.US_EAST_1) // dummy
																													// region
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create("UNQck41gBWFXuZK50Olz", "0rjQzcOdxYBR79VotiWwn35psqyAmv1KNJ2gaXSe")))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
	}

}
