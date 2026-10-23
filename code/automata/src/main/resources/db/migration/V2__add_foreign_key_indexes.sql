-- Indexes on the foreign key columns. PostgreSQL does not create them, and without them the joins
-- and the lookups by parent (a request's body properties, a job's target requests, the jobs of a
-- context, ...) scan the whole table, as do the checks made when a referenced row is deleted.
-- The foreign keys already covered by a primary key or unique constraint are left out.
-- No query depends on them: they can be dropped without changing any behavior.

CREATE INDEX idx_agent_context_host_id ON agent_context (host_id);
CREATE INDEX idx_agent_context_allowed_routines_agent_context_id ON agent_context_allowed_routines (agent_context_id);
CREATE INDEX idx_agent_context_allowed_routines_allowed_routines_id ON agent_context_allowed_routines (allowed_routines_id);
CREATE INDEX idx_agent_context_item_context_id ON agent_context_item (context_id);
CREATE INDEX idx_agent_context_item_equality_set_id ON agent_context_item (equality_set_id);
CREATE INDEX idx_agent_context_item_request_id ON agent_context_item (request_id);
CREATE INDEX idx_agent_context_tenants_agent_context_id ON agent_context_tenants (agent_context_id);
CREATE INDEX idx_agent_context_tenants_tenants_id ON agent_context_tenants (tenants_id);
CREATE INDEX idx_body_property_request_id ON body_property (request_id);
CREATE INDEX idx_body_property_response_id ON body_property (response_id);
CREATE INDEX idx_comparator_modifier_comparator_id ON comparator_modifier (comparator_id);
CREATE INDEX idx_comparator_modifier_modifier_id ON comparator_modifier (modifier_id);
CREATE INDEX idx_equalization_comparator_id ON equalization (comparator_id);
CREATE INDEX idx_equalization_host_id ON equalization (host_id);
CREATE INDEX idx_finding_host_id ON finding (host_id);
CREATE INDEX idx_finding_job_id ON finding (job_id);
CREATE INDEX idx_finding_request_id ON finding (request_id);
CREATE INDEX idx_finding_vulnerability_id ON finding (vulnerability_id);
CREATE INDEX idx_header_host_id ON header (host_id);
CREATE INDEX idx_host_program_id ON host (program_id);
CREATE INDEX idx_http_job_agent_context_id ON http_job (agent_context_id);
CREATE INDEX idx_http_job_host_id ON http_job (host_id);
CREATE INDEX idx_http_job_program_id ON http_job (program_id);
CREATE INDEX idx_http_job_routine_id ON http_job (routine_id);
CREATE INDEX idx_http_job_target_host_id ON http_job (target_host_id);
CREATE INDEX idx_http_job_tenant_id ON http_job (tenant_id);
CREATE INDEX idx_http_job_wordlists_http_job_id ON http_job_wordlists (http_job_id);
CREATE INDEX idx_http_job_wordlists_wordlists_id ON http_job_wordlists (wordlists_id);
CREATE INDEX idx_narrow_job_target_request_job_id ON narrow_job_target_request (job_id);
CREATE INDEX idx_narrow_job_target_request_target_request_id ON narrow_job_target_request (target_request_id);
CREATE INDEX idx_path_variable_request_id ON path_variable (request_id);
CREATE INDEX idx_program_out_of_scope_vulns_out_of_scope_vulns_id ON program_out_of_scope_vulns (out_of_scope_vulns_id);
CREATE INDEX idx_query_parameter_request_id ON query_parameter (request_id);
CREATE INDEX idx_request_host_id ON request (host_id);
CREATE INDEX idx_request_program_id ON request (program_id);
CREATE INDEX idx_request_tenant_id ON request (tenant_id);
CREATE INDEX idx_request_equality_relation_request_id ON request_equality_relation (request_id);
CREATE INDEX idx_request_equality_relation_set_id ON request_equality_relation (set_id);
CREATE INDEX idx_request_equality_set_equalization_id ON request_equality_set (equalization_id);
CREATE INDEX idx_response_body_property_host_id ON response_body_property (host_id);
CREATE INDEX idx_response_body_property_response_id ON response_body_property (response_id);
CREATE INDEX idx_routine_vulnerability_id ON routine (vulnerability_id);
CREATE INDEX idx_tenant_host_id ON tenant (host_id);
CREATE INDEX idx_vulnerability_parent_id ON vulnerability (parent_id);
CREATE INDEX idx_wide_job_target_host_job_id ON wide_job_target_host (job_id);
CREATE INDEX idx_wide_job_target_host_target_host_id ON wide_job_target_host (target_host_id);
CREATE INDEX idx_wordlist_vulnerabilities_vulnerabilities_id ON wordlist_vulnerabilities (vulnerabilities_id);
