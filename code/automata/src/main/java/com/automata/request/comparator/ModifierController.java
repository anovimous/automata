package com.automata.request.comparator;

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
import com.automata.request.comparator.common.dto.ModifierPatchRequest;
import com.automata.request.comparator.common.enums.Schema;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/modifiers")
@RequiredArgsConstructor
public class ModifierController {

	private final ModifierService modifierService;

	@GetMapping("/{modifierId}")
	public ResponseEntity<Modifier> getModifier(@PathVariable Long modifierId) {

		Modifier modifier = modifierService.getModifierById(modifierId);

		return ResponseEntity.ok(modifier);

	}

	@GetMapping("")
	public ResponseEntity<PageHolderResponse<Modifier>> getModifiers(@RequestParam(defaultValue = "") String query,
			@RequestParam(required = false) Schema schema,
			@PageableDefault(size = 30, sort = "creationDate", direction = Sort.Direction.ASC) Pageable pageable) {

		Page<Modifier> modifiers;

		if (schema == null)
			modifiers = modifierService.getModifiersPagedAndFilteredOnQueryString(query, pageable);
		else
			modifiers = modifierService.getModifiersPagedAndFilteredOnQueryStringAndSchema(query, schema, pageable);

		return ResponseEntity.ok(new PageHolderResponse<>(modifiers));

	}

	@PostMapping("")
	public ResponseEntity<Modifier> createModifier(@RequestBody Modifier modifier) {

		Modifier createdModifier = modifierService.createModifier(modifier);

		return ResponseEntity.status(201).body(createdModifier);

	}

	@PatchMapping("/{modifierId}")
	public ResponseEntity<Modifier> patchModifier(@PathVariable Long modifierId,
			@RequestBody ModifierPatchRequest patchRequest) {

		Modifier modifier = modifierService.patchModifier(modifierId, patchRequest);

		return ResponseEntity.ok(modifier);
	}

	@DeleteMapping("/{modifierId}")
	public ResponseEntity<Void> deleteModifier(@PathVariable Long modifierId) {

		modifierService.deleteModifier(modifierId);

		return ResponseEntity.status(204).build();

	}

}
