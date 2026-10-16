package com.automata.job.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.automata.host.Host;
import com.automata.request.Request;

class JobTargetRelationEqualityTest {

	@Test
	void narrowRelationsAreEqualOnlyByPersistedId() {
		NarrowJobTargetRequest a = new NarrowJobTargetRequest();
		NarrowJobTargetRequest b = new NarrowJobTargetRequest();
		a.setId(1L);
		b.setId(1L);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());

		b.setId(2L);
		assertNotEquals(a, b);
	}

	@Test
	void narrowRelationsWithoutIdAreOnlyEqualToThemselves() {
		NarrowJobTargetRequest a = new NarrowJobTargetRequest();

		assertEquals(a, a);
		assertNotEquals(a, new NarrowJobTargetRequest());
		assertNotEquals(a, null);
	}

	@Test
	void narrowFactorySetsBothSides() {
		NarrowHttpJob job = new NarrowHttpJob();
		Request request = new Request();

		NarrowJobTargetRequest relation = NarrowJobTargetRequest.of(job, request);

		assertSame(job, relation.getJob());
		assertSame(request, relation.getTargetRequest());
	}

	@Test
	void wideRelationsAreEqualOnlyByPersistedId() {
		WideJobTargetHost a = new WideJobTargetHost();
		WideJobTargetHost b = new WideJobTargetHost();
		a.setId(1L);
		b.setId(1L);

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());

		b.setId(2L);
		assertNotEquals(a, b);
		assertNotEquals(a, new WideJobTargetHost());
	}

	@Test
	void wideFactorySetsBothSides() {
		WideHttpJob job = new WideHttpJob();
		Host host = new Host();

		WideJobTargetHost relation = WideJobTargetHost.of(job, host);

		assertSame(job, relation.getJob());
		assertSame(host, relation.getTargetHost());
	}

}
