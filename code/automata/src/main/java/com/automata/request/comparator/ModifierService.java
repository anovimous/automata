package com.automata.request.comparator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.common.enums.Schema;

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

	public Page<Modifier> getModifiersPagedAndFilteredOnQueryString(String key, Pageable pageable) {

		return modifierRepo.findByKeyContainingIgnoreCase(key, pageable);

	}

	public Page<Modifier> getModifiersPagedAndFilteredOnQueryStringAndSchema(String key, Schema schema,
			Pageable pageable) {

		return modifierRepo.findByKeyContainingIgnoreCaseAndSchema(key, schema, pageable);

	}

	public Modifier patchModifier(Long modifierId, ModifierPatchRequest patchRequest) {

		Modifier modifier = modifierRepo.findById(modifierId)
				.orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

		patchRequest.key().ifPresent(modifier::setKey);

		patchRequest.description().ifPresent(modifier::setDescription);

		patchRequest.isAvailable().ifPresent(modifier::setAvailable);

		modifierRepo.save(modifier);

		return modifier;

	}

	public void deleteModifier(Long modifierId) {

		Modifier modifier = modifierRepo.findById(modifierId)
				.orElseThrow(() -> new EntityNotFoundException("Modifier not found"));

		modifierRepo.delete(modifier);

	}

}
