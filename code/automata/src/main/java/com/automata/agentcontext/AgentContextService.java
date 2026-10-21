package com.automata.agentcontext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.automata.agentcontext.common.dto.AgentContextCreationRequest;
import com.automata.agentcontext.common.dto.AgentContextItemAddRequest;
import com.automata.agentcontext.common.dto.AgentContextPatchRequest;
import com.automata.agentcontext.common.dto.ResolvedTarget;
import com.automata.agentcontext.common.enums.AgentContextItemType;
import com.automata.agentcontext.common.enums.RepresentativeStrategy;
import com.automata.host.Host;
import com.automata.host.HostRepository;
import com.automata.job.utils.HttpJobUtils;
import com.automata.request.Request;
import com.automata.request.RequestRepository;
import com.automata.request.equalityset.RequestEqualityRelation;
import com.automata.request.equalityset.RequestEqualityRelationRepository;
import com.automata.request.equalityset.RequestEqualitySet;
import com.automata.request.equalityset.RequestEqualitySetRepository;
import com.automata.routine.Routine;
import com.automata.routine.RoutineRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentContextService {

	private final AgentContextRepository contextRepo;

	private final AgentContextItemRepository itemRepo;

	private final HostRepository hostRepo;

	private final RoutineRepository routineRepo;

	private final RequestRepository requestRepo;

	private final RequestEqualitySetRepository equalitySetRepo;

	private final RequestEqualityRelationRepository equalityRelationRepo;

	@Transactional(readOnly = true)
	public AgentContext getContext(Long contextId) {
		return contextRepo.findById(contextId)
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));
	}

	@Transactional(readOnly = true)
	public AgentContext getContextByUuid(java.util.UUID uuid) {
		return contextRepo.findByUuid(uuid)
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));
	}

	@Transactional(readOnly = true)
	public Page<AgentContext> getContextsByHost(Long hostId, Pageable pageable) {
		return contextRepo.findByHostId(hostId, pageable);
	}

	@Transactional
	public AgentContext createContext(AgentContextCreationRequest request) {

		Host host = hostRepo.findById(request.hostId())
				.orElseThrow(() -> new EntityNotFoundException("Host not found"));

		List<Routine> allowedRoutines = new ArrayList<>();
		if (request.allowedRoutineIds() != null)
			allowedRoutines = routineRepo.findAllById(request.allowedRoutineIds());

		AgentContext context = AgentContext.builder().name(request.name()).description(request.description())
				.host(host).defaultAutoApproval(request.defaultAutoApproval())
				.representativeStrategy(request.representativeStrategy()).allowedRoutines(allowedRoutines)
				.defaultDuration(request.defaultDuration()).defaultVerbosity(request.defaultVerbosity())
				.defaultPriority(request.defaultPriority()).maxRate(request.maxRate()).build();

		return contextRepo.save(context);

	}

	@Transactional
	public void patchContext(Long contextId, AgentContextPatchRequest patch) {

		AgentContext context = contextRepo.findById(contextId)
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));

		patch.name().ifPresent(context::setName);

		if (patch.setDescriptionNull())
			context.setDescription(null);
		else
			patch.description().ifPresent(context::setDescription);

		patch.defaultAutoApproval().ifPresent(context::setDefaultAutoApproval);
		patch.representativeStrategy().ifPresent(context::setRepresentativeStrategy);

		if (patch.allowedRoutineIds() != null)
			context.setAllowedRoutines(routineRepo.findAllById(patch.allowedRoutineIds()));

		if (patch.setDefaultDurationNull())
			context.setDefaultDuration(null);
		else
			patch.defaultDuration().ifPresent(context::setDefaultDuration);

		if (patch.setDefaultVerbosityNull())
			context.setDefaultVerbosity(null);
		else
			patch.defaultVerbosity().ifPresent(context::setDefaultVerbosity);

		if (patch.setDefaultPriorityNull())
			context.setDefaultPriority(null);
		else
			patch.defaultPriority().ifPresent(context::setDefaultPriority);

		if (patch.setMaxRateNull())
			context.setMaxRate(null);
		else
			patch.maxRate().ifPresent(context::setMaxRate);

		contextRepo.save(context);

	}

	@Transactional
	public void deleteContext(Long contextId) {

		AgentContext context = contextRepo.findById(contextId)
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));

		itemRepo.deleteAll(context.getItems());
		contextRepo.delete(context);

	}

	@Transactional
	public AgentContextItem addItem(Long contextId, AgentContextItemAddRequest request) {

		AgentContext context = contextRepo.findById(contextId)
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));

		AgentContextItem.AgentContextItemBuilder builder = AgentContextItem.builder();
		builder.context(context);
		builder.itemType(request.itemType());

		if (request.itemType() == AgentContextItemType.REQUEST) {
			if (request.requestId() == null)
				throw new IllegalArgumentException("requestId is required for REQUEST items");

			Request req = requestRepo.findById(request.requestId())
					.orElseThrow(() -> new EntityNotFoundException("Request not found"));

			if (!req.getHost().getId().equals(context.getHost().getId()))
				throw new IllegalArgumentException("Request does not belong to the context's host");

			if (itemRepo.existsByContextIdAndRequestId(contextId, request.requestId()))
				throw new IllegalArgumentException("Request is already in this context");

			builder.request(req);

		} else {
			if (request.equalitySetId() == null)
				throw new IllegalArgumentException("equalitySetId is required for EQUALITY_SET items");

			RequestEqualitySet set = equalitySetRepo.findById(request.equalitySetId())
					.orElseThrow(() -> new EntityNotFoundException("RequestEqualitySet not found"));

			if (!set.getEqualization().getHost().getId().equals(context.getHost().getId()))
				throw new IllegalArgumentException("Equality set does not belong to the context's host");

			if (itemRepo.existsByContextIdAndEqualitySetId(contextId, request.equalitySetId()))
				throw new IllegalArgumentException("Equality set is already in this context");

			builder.equalitySet(set);
		}

		return itemRepo.save(builder.build());

	}

	@Transactional
	public void removeItem(Long contextId, Long itemId) {

		AgentContextItem item = itemRepo.findById(itemId)
				.orElseThrow(() -> new EntityNotFoundException("AgentContextItem not found"));

		if (!item.getContext().getId().equals(contextId))
			throw new IllegalArgumentException("Item does not belong to this context");

		itemRepo.delete(item);

	}

	@Transactional(readOnly = true)
	public List<ResolvedTarget> resolveContextTargets(Long contextId) {

		AgentContext context = contextRepo.findById(contextId)
				.orElseThrow(() -> new EntityNotFoundException("AgentContext not found"));

		Map<Long, ResolvedTarget> targetsByRequestId = new LinkedHashMap<>();

		for (AgentContextItem item : context.getItems()) {
			if (item.getItemType() == AgentContextItemType.REQUEST) {

				Request req = item.getRequest();
				boolean eligible = HttpJobUtils.isJobEligible(req);
				ResolvedTarget target = ResolvedTarget.builder().requestId(req.getId())
						.automataJobEligible(eligible).autoApproval(context.isDefaultAutoApproval())
						.sourceItemType(AgentContextItemType.REQUEST).build();
				targetsByRequestId.put(req.getId(), target);

			} else {

				RequestEqualitySet set = item.getEqualitySet();
				boolean setAutoApproval = set.isAutoApproval();

				if (context.getRepresentativeStrategy() == RepresentativeStrategy.LATEST) {
					Request latest = equalityRelationRepo.findTopBySetOrderByRequestInsertionDateDesc(set);
					if (latest != null) {
						boolean eligible = HttpJobUtils.isJobEligible(latest);
						ResolvedTarget target = ResolvedTarget.builder().requestId(latest.getId())
								.automataJobEligible(eligible).autoApproval(setAutoApproval)
								.sourceItemType(AgentContextItemType.EQUALITY_SET)
								.sourceEqualitySetId(set.getId()).build();
						targetsByRequestId.put(latest.getId(), target);
					}
				} else {
					List<RequestEqualityRelation> relations = equalityRelationRepo.findAllBySet(set);
					for (RequestEqualityRelation rel : relations) {
						Request req = rel.getRequest();
						boolean eligible = HttpJobUtils.isJobEligible(req);
						ResolvedTarget target = ResolvedTarget.builder().requestId(req.getId())
								.automataJobEligible(eligible).autoApproval(setAutoApproval)
								.sourceItemType(AgentContextItemType.EQUALITY_SET)
								.sourceEqualitySetId(set.getId()).build();
						targetsByRequestId.put(req.getId(), target);
					}
				}
			}
		}

		return new ArrayList<>(targetsByRequestId.values());

	}

}
