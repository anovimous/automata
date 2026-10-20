package com.automata.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.automata.job.api.dto.GenericHttpJobDetailsDto;
import com.automata.job.domain.model.embedded.HttpJobGenericDetails;
import com.automata.job.domain.model.embedded.TargetSelector;
import com.automata.job.domain.model.enums.HttpJobScope;
import com.automata.job.domain.model.enums.JobState;
import com.automata.job.domain.model.enums.ResultsVerbosity;
import com.automata.job.domain.model.enums.SelectorType;
import com.automata.job.repository.HttpJobRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;
import com.automata.wordlist.Wordlist;
import com.automata.wordlist.WordlistRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class HttpJobCreationServiceDraftHelpersTest {

	@Mock
	HttpJobRepository jobRepo;
	@Mock
	RoutineRepository routineRepo;
	@Mock
	WordlistRepository wordlistRepo;
	@Mock
	JobConfigFileAsyncService configFileService;

	@InjectMocks
	HttpJobCreationService service;

	@BeforeEach
	void startTransactionSynchronization() {
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void clearTransactionSynchronization() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	private static GenericHttpJobDetailsDto genericDto(Set<Long> wordlistsIds) {
		return new GenericHttpJobDetailsDto(ResultsVerbosity.NORMAL, 2, 10, 1L, wordlistsIds,
				new TargetSelector(SelectorType.SINGLE_HOST, null), List.of(), null);
	}

	@Test
	void findRoutineReturnsTheRoutineOfTheDto() {
		Routine routine = new Routine();
		when(routineRepo.findById(1L)).thenReturn(Optional.of(routine));

		assertSame(routine, service.findRoutine(genericDto(null)));
	}

	@Test
	void findRoutineOfAnUnknownRoutineIsReported() {
		when(routineRepo.findById(1L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> service.findRoutine(genericDto(null)));
	}

	@Test
	void draftDetailsStartInDraftWithTheScopeAndValuesOfTheDto() {
		HttpJobGenericDetails details = service.draftDetails(genericDto(null), HttpJobScope.WIDE);

		assertEquals(HttpJobScope.WIDE, details.getHttpJobScope());
		assertEquals(JobState.DRAFT, details.getCurrentState());
		assertEquals(ResultsVerbosity.NORMAL, details.getVerbosity());
		assertEquals(2, details.getPriority());
		assertEquals(10, details.getRate());
	}

	@Test
	void draftDetailsKeepTheScopeTheyAreAskedFor() {
		assertEquals(HttpJobScope.NARROW, service.draftDetails(genericDto(null), HttpJobScope.NARROW).getHttpJobScope());
	}

	@Test
	void findWordlistsWithoutIdsIsEmptyAndAsksNothing() {
		assertTrue(service.findWordlists(genericDto(null)).isEmpty());

		verifyNoInteractions(wordlistRepo);
	}

	@Test
	void findWordlistsLoadsTheWordlistsOfTheIds() {
		List<Wordlist> wordlists = List.of(new Wordlist());
		when(wordlistRepo.findAllById(Set.of(3L))).thenReturn(wordlists);

		assertSame(wordlists, service.findWordlists(genericDto(Set.of(3L))));
	}

	@Test
	void configFilesArePreparedOnlyAfterTheCommit() {
		service.prepareConfigFilesAfterCommit(7L);

		verifyNoInteractions(configFileService);

		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

		verify(configFileService).prepareConfigFiles(7L);
	}

}
