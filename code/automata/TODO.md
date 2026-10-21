# AI Test Flow

The flow will be that: 1. the ai agent checks the current context, the current context may be a request or a list of
  requests or a list of equality sets, the current context will be the target of the AI agent. Note that multiple contexts may be configured, a UUID is
  assigned for each context, i can give the UUID to the AI agent and tell it to test the context with the following UUID. 2. The AI agent will create jobs
  for each request that it wants to test, note that equality sets are not job targets. If the equality set that contains the target request of a job has the
  flag autoApproval set to true then the job can be moved to TOQUEUE without human approval, if false then moving it to TOQUEUE needs explicit human
  approval. When creating a job, the AIDescrition is set for it with a description of the goal to be achieved from this job, or in other words, what does
  the result of this job let us know or verify. 3. When a job ends, the AI agent reviews the result and sets the AIResult field with the summary of the
  result of the job. 4. If a vulnerability is verified, the AI agent creates a new finding that is associated with an equality set and a host, and
  optionally associated with a specific job if a job is what found the vulnerability

# TODO

- write unit tests covering controllers, services, and utils in the request and response domain. For the subpackages inside the request domain, only test for the packages body and equalityset. Run the unit tests and inform me of any bugs in my code
- create MCP server (agent interacts only via MCP, never REST directly)
- Job finish notification (Polling vs webhook vs SSE)
- Wire agentContext ManyToOne on HttpJob to AgentContext entity
- OpenAPI annotations (@Operation, @Tag, @Schema) on all endpoints
- RAG integration for findings with human verdicts


# DONE
> Oct 21
- AI agent design plan implemented (steps 1-8)
- Host enrichment: description, technologies (jsonb), notes fields on Host entity
- Equality set autoApproval: new field + GET/PATCH endpoints for equality sets
- AI fields on HttpJob: aiDescription, aiResult, createdBy (JobCreator enum), approved
- Routine configSchema: jsonb field for MCP server consumption
- AI job endpoint: POST /api/jobs/http/ai, approval gate, ai-result endpoint
- AgentContext entity: scopes AI agent to a host with request/equality-set items, resolved-targets with LATEST/AI_JUDGEMENT strategies
- Finding entity: vulnerability findings with CVSS severity, confidence, status, human review workflow
- Removed POST /api/jobs/http/sync test endpoint
> Oct 20
- Work through the components code review markdown in .claude folder
> Oct 19
- remove the GLOBAL scope
- check the remaining architectural changes referenced inside the file in .claude and implement them
> Oct 16
- Edit the job domain to reflect the changes that are made on oct 15 in the request domain (Tomorrow you can say "read .claude/job-domain-fixup.md and fix the job domain".)
- Review the job domain and suggest architecture and code-level changes, and what parts are missing?
> Oct 15
- commit all changes
- Edit the request domain into the new form
- Accept all content types for requests, only store detailed body components for json and POST requests
- Bodies of Multipart requests are stored as files in rustfs, detailed components of the request are still stored(except the body obviously)
- Response detailed components are always computated and stored (except for the body properties which are only stored for JSON responses)
- For any other response other than JSON, store the body as a file in rustfs, and add a controller method to download it
- Use different rustfs buckets for different purposes, make sure to edit where required in the configs and in docker related files so that the required buckets are created and available
- Make sure that the app now supports different content types, since the ones which are mentioned in enums are minimal
