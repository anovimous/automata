package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.automata.job.domain.model.HttpJob;
import com.automata.job.domain.model.NarrowHttpJob;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.domain.valueobject.JobDetailsFileContainer;
import com.automata.job.domain.valueobject.WorkerWordlist;
import com.automata.job.repository.HttpJobRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.tenant.authentication.AuthenticationRepository;
import com.automata.wordlist.Wordlist;

@ExtendWith(MockitoExtension.class)
class JobDetailsServiceTest {

	@Mock
	HttpJobRepository httpJobRepo;
	@Mock
	AuthenticationRepository authRepo;
	@Mock
	RoutineRepository routineRepo;

	@InjectMocks
	JobDetailsService service;

	private Wordlist wordlist(long id, String name, String sha, long size) {
		Wordlist wordlist = new Wordlist();
		wordlist.setId(id);
		wordlist.setName(name);
		wordlist.setSha256(sha);
		wordlist.setSizeBytes(size);
		return wordlist;
	}

	private HttpJob job() {
		Routine routine = new Routine();
		routine.setId(2L);
		routine.setKey("fuzz");

		HttpJobGenericDetails details = HttpJobGenericDetails.builder().httpJobScope(HttpJobScope.NARROW)
				.targetSelector(new TargetSelector(SelectorType.SINGLE_REQUEST, null)).rate(5).build();

		NarrowHttpJob job = new NarrowHttpJob();
		job.setId(42L);
		job.setRoutine(routine);
		job.setGenericDetails(details);

		when(routineRepo.findById(2L)).thenReturn(Optional.of(routine));
		return job;
	}

	@Test
	void wordlistsOfTheJobAreGivenWithTheirHashSizeAndNameInTheOrderOfTheRepository() {
		HttpJob job = job();
		when(httpJobRepo.findWordlistsByJobId(42L)).thenReturn(List.of(wordlist(3L, "b", "bb", 20L), wordlist(9L, null, "cc", 5L)));

		JobDetailsFileContainer container = service.createJobDetailsContainer(job);

		assertEquals(List.of(new WorkerWordlist(3L, "b", "bb", 20L), new WorkerWordlist(9L, null, "cc", 5L)),
				container.wordlists());
	}

	@Test
	void jobWithoutWordlistsHasAnEmptyList() {
		HttpJob job = job();
		when(httpJobRepo.findWordlistsByJobId(42L)).thenReturn(List.of());

		assertEquals(List.of(), service.createJobDetailsContainer(job).wordlists());
	}

	@Test
	void workerWordlistTakesTheFileFactsOfTheWordlist() {
		assertEquals(new WorkerWordlist(3L, "b", "bb", 20L), WorkerWordlist.of(wordlist(3L, "b", "bb", 20L)));
	}

}
