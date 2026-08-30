package com.minicoze.platform.agent;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import com.minicoze.platform.tool.ToolResponse;
import com.minicoze.platform.tool.ToolService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService service; private final ToolService tools;
    public AgentController(AgentService service, ToolService tools) { this.service = service; this.tools=tools; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) AgentResponse create(@Valid @RequestBody AgentRequest request) { return service.create(request); }
    @GetMapping List<AgentResponse> list() { return service.list(); }
    @GetMapping("/{id}") AgentResponse get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/{id}") AgentResponse update(@PathVariable UUID id, @Valid @RequestBody AgentRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id) { service.delete(id); }
    @GetMapping("/{id}/tools") List<ToolResponse> listTools(@PathVariable UUID id) { service.getEntity(id); return tools.allowedForAgent(id).stream().map(ToolResponse::from).toList(); }
    @PutMapping("/{id}/tools") List<ToolResponse> replaceTools(@PathVariable UUID id, @Valid @RequestBody AgentToolBindingRequest request) { service.getEntity(id); return tools.replaceBindings(id, request.toolIds()); }
}
