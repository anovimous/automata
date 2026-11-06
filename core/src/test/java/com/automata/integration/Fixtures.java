package com.automata.integration;

import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.automata.host.Host;
import com.automata.host.common.enums.Scope;
import com.automata.program.Program;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.WideHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.embedded.NarrowTargetConfig;
import com.automata.job.domain.model.embedded.WideTargetConfig;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.request.Request;
import com.automata.routine.Routine;
import com.automata.routine.common.enums.Duration;
import com.automata.routine.common.enums.Protocol;
import com.automata.routine.common.enums.ResultContentType;
import com.automata.routine.common.enums.TargetCardinalityPair;
import com.automata.request.common.enums.Method;
import com.automata.request.common.enums.Source;
import com.automata.request.comparator.Comparator;
import com.automata.request.equalityset.Equalization;
import com.automata.request.equalityset.RequestEqualityRelation;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.parameter.QueryParameter;
import com.automata.tenant.Tenant;
import com.automata.vulnerability.Vulnerability;

/** Persists the rows the integration tests hang their data on. Every method flushes, so a constraint problem shows where it is caused. */
public final class Fixtures {

	private final TestEntityManager em;

	public Fixtures(TestEntityManager em) {
		this.em = em;
	}

	public Program program(String name) {
		Program program = new Program();
		program.setName(name);
		program.setProgramRateLimit(100);
		return persist(program);
	}

	public Vulnerability vulnerability(String name, Vulnerability parent) {
		Vulnerability vulnerability = new Vulnerability();
		vulnerability.setName(name);
		vulnerability.setParent(parent);
		return persist(vulnerability);
	}

	public Host host(Program program, String name) {
		Host host = new Host();
		host.setHost(name);
		host.setLevel(name.split("\\.").length - 2);
		host.setScope(Scope.FQDN);
		host.setHostRateLimit(100);
		host.setShortRateLimit(20);
		host.setLongRateLimit(20);
		host.setProgram(program);
		return persist(host);
	}

	public Tenant tenant(Host host, String name) {
		Tenant tenant = Tenant.builder().name(name).email(name + "@example.test").host(host).build();
		return persist(tenant);
	}

	public Request request(Host host, Method method, String computatedPath) {
		Request request = new Request();
		request.setProgram(host.getProgram());
		request.setHost(host);
		request.setMethod(method);
		request.setComputatedPath(computatedPath);
		request.setSource(Source.BURP);
		request.setVersion("HTTP/1.1");
		return persist(request);
	}

	public QueryParameter parameter(Request request, String name, String value) {
		QueryParameter parameter = QueryParameter.of(name, value);
		parameter.setRequest(request);
		request.getParameters().add(parameter);
		return persist(parameter);
	}

	public Routine routine(String key) {
		Routine routine = new Routine();
		routine.setKey(key);
		routine.setAvailableAtConsumer(true);
		routine.setProtocol(Protocol.HTTP);
		routine.setResultContentType(ResultContentType.TEXT);
		routine.setAllowedTargets(java.util.EnumSet.allOf(TargetCardinalityPair.class));
		return persist(routine);
	}

	private static HttpJobGenericDetails details(HttpJobScope scope, JobState state, int rate, int priority) {
		return HttpJobGenericDetails.builder().httpJobScope(scope).currentState(state).verbosity(ResultsVerbosity.NORMAL)
				.priority(priority).rate(rate).build();
	}

	public NarrowHttpJob narrowJob(Routine routine, Host host, JobState state, int rate, Duration duration, int priority) {
		NarrowHttpJob job = NarrowHttpJob.builder().genericDetails(details(HttpJobScope.NARROW, state, rate, priority))
				.program(host.getProgram()).routine(routine).duration(duration).host(host)
				.targetConfig(NarrowTargetConfig.builder().targetHost(host).build()).build();
		return persist(job);
	}

	public WideHttpJob wideJob(Routine routine, Program program, JobState state, int rate, int priority) {
		WideHttpJob job = WideHttpJob.builder().genericDetails(details(HttpJobScope.WIDE, state, rate, priority)).program(program)
				.routine(routine).targetConfig(WideTargetConfig.builder().build()).build();
		return persist(job);
	}

	/** An equality set of the host holding the given requests (its equalization and comparator are created for it). */
	public RequestEqualitySet equalitySet(Host host, boolean autoApproval, Request... members) {
		Comparator comparator = persist(Comparator.builder().name("c").build());
		Equalization equalization = persist(Equalization.builder().host(host).comparator(comparator).build());
		RequestEqualitySet set = persist(RequestEqualitySet.builder().alias("set").numberOfRequests(members.length)
				.equalization(equalization).autoApproval(autoApproval).build());
		for (Request member : members)
			persist(RequestEqualityRelation.builder().set(set).request(member).build());
		return set;
	}

	public <T> T persist(T entity) {
		em.persist(entity);
		em.flush();
		return entity;
	}

	public void clear() {
		em.flush();
		em.clear();
	}
}
