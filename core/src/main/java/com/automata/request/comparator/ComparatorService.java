package com.automata.request.comparator;

import java.util.List;
import java.util.stream.Collectors;
import java.util.TreeSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.comparator.common.dto.ComparatorPatchRequest;
import com.automata.request.comparator.common.enums.Schema;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComparatorService {

	private final ComparatorRepository comparatorRepo;

	private final ModifierRepository modifierRepo;

	public Comparator getComparatorById(Long comparatorId) {

		return comparatorRepo.findById(comparatorId)
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

	}

	public Page<Comparator> getComparatorsPagedAndFiltered(String name, Schema schema, Pageable pageable) {

		Specification<Comparator> spec = ComparatorUtils.buildSpecification(name, schema);

		return comparatorRepo.findAll(spec, pageable);

	}

	@Transactional
	public Comparator createComparator(Comparator comparator, Set<Long> modifierIds) {

		if (modifierIds == null || modifierIds.isEmpty())
			throw new IllegalArgumentException("A comparator needs at least one modifier");

		List<Modifier> modifiers = modifierRepo.findAllById(modifierIds);

		Set<Long> found = modifiers.stream().map(Modifier::getId).collect(Collectors.toSet());
		Set<Long> missing = new TreeSet<>(modifierIds);
		missing.removeAll(found);

		if (!missing.isEmpty())
			throw new EntityNotFoundException("Modifier not found: " + missing);

		// a modifier which is not available can't hash a request, and equalizing with it would fail
		Set<Long> unavailable = modifiers.stream().filter(modifier -> !modifier.isAvailable()).map(Modifier::getId)
				.collect(Collectors.toCollection(TreeSet::new));

		if (!unavailable.isEmpty())
			throw new IllegalArgumentException("Modifier not available: " + unavailable);

		comparator.setModifiers(modifiers);

		return comparatorRepo.save(comparator);

	}

	@Transactional
	public Comparator patchComparator(Long comparatorId, ComparatorPatchRequest patchRequest) {

		Comparator comparator = comparatorRepo.findById(comparatorId)
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

		patchRequest.name().ifPresent(comparator::setName);

		if (patchRequest.setSchemaNull())
			comparator.setSchema(null);
		else
			patchRequest.schema().ifPresent(comparator::setSchema);

		comparatorRepo.save(comparator);

		return comparator;

	}

	@Transactional
	public void deleteComparator(Long comparatorId) {

		Comparator comparator = comparatorRepo.findById(comparatorId)
				.orElseThrow(() -> new EntityNotFoundException("Comparator not found"));

		comparatorRepo.delete(comparator);

	}

}
