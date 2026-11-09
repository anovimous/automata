// Rules of the texts of a routine (RoutineCreationRequest and RoutineService.patchRoutine in the core).

/** Routine.MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH in the core. */
export const MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH = 5000;

/** What is wrong with the description and the usage description typed in a form, or null when they are acceptable. */
export function routineTextsProblem(description: string, usageDescriptionForAgent: string): string | null {
  if (!description.trim()) return "The description is required.";
  if (!usageDescriptionForAgent.trim()) return "The usage description for the agent is required.";
  if (usageDescriptionForAgent.length > MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH) {
    return `The usage description for the agent is limited to ${MAX_USAGE_DESCRIPTION_FOR_AGENT_LENGTH} characters.`;
  }
  return null;
}
