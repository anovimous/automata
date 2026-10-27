package com.automata.integration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import com.automata.AutomataApplication;

/**
 * Meta-annotation for the integration tests that need a database: the real entities, repositories and JPQL queries on a real PostgreSQL, with the real Flyway
 * migrations applied and Hibernate in {@code validate} mode (so these tests also prove that migrations and entities agree). They only run when
 * {@code AUTOMATA_IT_DB_URL} (+ {@code AUTOMATA_IT_DB_USER}, {@code AUTOMATA_IT_DB_PASSWORD}) point at an empty throwaway database, for instance:
 * <pre>
 * docker run -d --rm --name automata-it-pg -e POSTGRES_DB=it -e POSTGRES_USER=it -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16
 * AUTOMATA_IT_DB_URL=jdbc:postgresql://localhost:55432/it AUTOMATA_IT_DB_USER=it AUTOMATA_IT_DB_PASSWORD=it ./mvnw test
 * </pre>
 * Every test method runs in a transaction that is rolled back.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfEnvironmentVariable(named = "AUTOMATA_IT_DB_URL", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AutomataApplication.class)
@TestPropertySource(properties = { "spring.datasource.url=${AUTOMATA_IT_DB_URL}",
		"spring.datasource.username=${AUTOMATA_IT_DB_USER:it}", "spring.datasource.password=${AUTOMATA_IT_DB_PASSWORD:it}",
		"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate" })
public @interface RepositoryIntegrationTest {
}
