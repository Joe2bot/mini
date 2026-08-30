package com.minicoze.platform.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicoze.platform.agent.AgentToolBindingEntity;
import com.minicoze.platform.agent.AgentToolBindingRepository;
import com.minicoze.platform.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolService {
    private final ToolRepository tools; private final AgentToolBindingRepository bindings; private final ObjectMapper mapper;
    public ToolService(ToolRepository tools, AgentToolBindingRepository bindings, ObjectMapper mapper) { this.tools=tools; this.bindings=bindings; this.mapper=mapper; }
    @Transactional public ToolResponse create(ToolRequest r) { validateSchema(r.inputSchema()); return ToolResponse.from(tools.save(new ToolEntity(UUID.randomUUID(), r.name(), r.description(), r.inputSchema(), r.sourceType(), r.sourceId(), r.enabled()))); }
    @Transactional(readOnly=true) public List<ToolResponse> list() { return tools.findAll().stream().map(ToolResponse::from).toList(); }
    @Transactional(readOnly=true) public ToolEntity getEntity(UUID id) { return tools.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"TOOL_NOT_FOUND","Tool not found")); }
    @Transactional(readOnly=true) public ToolResponse get(UUID id) { return ToolResponse.from(getEntity(id)); }
    @Transactional public ToolResponse update(UUID id, ToolRequest r) { validateSchema(r.inputSchema()); ToolEntity tool=getEntity(id); tool.update(r.name(),r.description(),r.inputSchema(),r.sourceType(),r.sourceId(),r.enabled()); return ToolResponse.from(tool); }
    @Transactional public void delete(UUID id) { getEntity(id); bindings.deleteByToolId(id); tools.deleteById(id); }
    @Transactional(readOnly=true) public List<ToolEntity> allowedForAgent(UUID agentId) { return bindings.findByAgentIdAndEnabledTrue(agentId).stream().map(AgentToolBindingEntity::getToolId).map(this::getEntity).filter(ToolEntity::isEnabled).toList(); }
    @Transactional public List<ToolResponse> replaceBindings(UUID agentId, List<UUID> ids) {
        bindings.deleteByAgentId(agentId);
        ids.stream().distinct().map(this::getEntity).forEach(tool -> bindings.save(new AgentToolBindingEntity(agentId, tool.getId())));
        return allowedForAgent(agentId).stream().map(ToolResponse::from).toList();
    }
    private void validateSchema(String raw) { try { JsonNode schema=mapper.readTree(raw); if(!schema.isObject()) throw new IllegalArgumentException(); } catch(Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TOOL_SCHEMA","inputSchema must be a JSON Schema object"); } }
}
