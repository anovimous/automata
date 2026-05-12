package com.automata.request.comparator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;

import jakarta.persistence.EntityNotFoundException;
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

	public Page<Modifier> getModifiersPagedAndFilteredOnHashTarget(HashTarget target, Pageable pageable) {

		return modifierRepo.findByTarget(target, pageable);

	}

	public Page<Modifier> getModifiersPagedAndFilteredOnHashTargetAndSchema(HashTarget target, Schema schema,
			Pageable pageable) {

		return modifierRepo.findByTargetAndSchema(target, schema, pageable);

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
