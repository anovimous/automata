package com.automata.request.comparator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModifierService {

	private final ModifierRepository modifierRepo;

	public Modifier getModifierById(Long modifierId) {

		return modifierRepo.findById(modifierId).orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

	}

	public Modifier createModifier(Modifier modifier) {

		return modifierRepo.save(modifier);

	}

	public Page<Modifier> getModifiersPagedAndFilteredOnHashTargetAndSchema(HashTarget target, Schema schema,
			Pageable pageable) {

		Specification<Modifier> spec = (root, query, cb) -> {

			List<Predicate> predicates = new ArrayList<>();

			if (target != null) {
				predicates.add(cb.equal(root.get("target"), target));
			}

			if (schema != null) {
				predicates.add(cb.equal(root.get("schema"), schema));
			}

			return cb.and(predicates.toArray(Predicate[]::new));
		};

		return modifierRepo.findAll(spec, pageable);

	}

	@Transactional
	public Modifier patchModifier(Long modifierId, ModifierPatchRequest patchRequest) {

		Modifier modifier = modifierRepo.findById(modifierId)
				.orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

		patchRequest.description().ifPresent(modifier::setDescription);

		patchRequest.isAvailable().ifPresent(modifier::setAvailable);

		modifierRepo.save(modifier);

		return modifier;

	}

	@Transactional
	public void deleteModifier(Long modifierId) {

		Modifier modifier = modifierRepo.findById(modifierId)
				.orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

		modifierRepo.delete(modifier);

	}

}
