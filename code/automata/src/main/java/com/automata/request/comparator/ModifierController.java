package com.automata.request.comparator;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.automata.common.dto.response.PageHolderResponse;
import com.automata.request.comparator.common.dto.ModifierCreationRequest;
import com.automata.request.comparator.common.dto.ModifierDetailedResponse;
import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.common.dto.ModifierSummaryResponse;
import com.automata.request.comparator.common.enums.Schema;
import com.automata.request.comparator.hash.HashTarget;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/modifiers")
@RequiredArgsConstructor
public class ModifierController {

	private final ModifierService modifierService;

	@GetMapping("/{modifierId}")
	public ResponseEntity<ModifierDetailedResponse> getModifier(@PathVariable Long modifierId) {

		Modifier modifier = modifierService.getModifierById(modifierId);

		ModifierDetailedResponse response = ModifierMapper.toDetailedResponse(modifier);

		return ResponseEntity.ok(response);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<ModifierSummaryResponse>> getModifiers(
			@RequestParam(required = false) HashTarget target, @RequestParam(required = false) Schema schema,
			@PageableDefault(size = 30, sort = { "creationDate" }, direction = Sort.Direction.DESC) Pageable pageable) {

		Page<Modifier> modifiers = modifierService.getModifiersPagedAndFilteredOnHashTargetAndSchema(target, schema,
				pageable);

		Page<ModifierSummaryResponse> respones = modifiers.map(ModifierMapper::toSummaryResponse);

		return ResponseEntity.ok(new PageHolderResponse<>(respones));

	}

	@PostMapping("")
	public ResponseEntity<Void> createModifier(@RequestBody ModifierCreationRequest req) {

		Modifier toCreateModifier = ModifierMapper.toModifier(req);

		Modifier createdModifier = modifierService.createModifier(toCreateModifier);

		return ResponseEntity.status(201).location(URI.create("/api/modifiers/" + createdModifier.getId())).build();

	}

	@PatchMapping("/{modifierId}")
	public ResponseEntity<Void> patchModifier(@PathVariable Long modifierId,
			@RequestBody ModifierPatchRequest patchRequest) {

		modifierService.patchModifier(modifierId, patchRequest);

		return ResponseEntity.status(204).build();
	}

	@DeleteMapping("/{modifierId}")
	public ResponseEntity<Void> deleteModifier(@PathVariable Long modifierId) {

		modifierService.deleteModifier(modifierId);

		return ResponseEntity.status(204).build();

	}

}
