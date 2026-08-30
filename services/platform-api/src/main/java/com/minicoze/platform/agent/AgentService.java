package com.minicoze.platform.agent;

import com.minicoze.platform.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {
    private final AgentRepository repository; private final AgentToolBindingRepository bindings;
    public AgentService(AgentRepository repository, AgentToolBindingRepository bindings) { this.repository = repository; this.bindings=bindings; }
    @Transactional public AgentResponse create(AgentRequest r) { return AgentResponse.from(repository.save(new AgentEntity(UUID.randomUUID(), r.name(), r.description(), r.systemPrompt(), r.modelProvider(), r.modelName(), r.temperature(), r.maxTokens()))); }
    @Transactional(readOnly = true) public List<AgentResponse> list() { return repository.findAll().stream().map(AgentResponse::from).toList(); }
    @Transactional(readOnly = true) public AgentEntity getEntity(UUID id) { return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent not found")); }
    @Transactional(readOnly = true) public AgentResponse get(UUID id) { return AgentResponse.from(getEntity(id)); }
    @Transactional public AgentResponse update(UUID id, AgentRequest r) { AgentEntity a=getEntity(id); a.update(r.name(),r.description(),r.systemPrompt(),r.modelProvider(),r.modelName(),r.temperature(),r.maxTokens()); return AgentResponse.from(a); }
    @Transactional public void delete(UUID id) { getEntity(id); bindings.deleteByAgentId(id); repository.deleteById(id); }
}
