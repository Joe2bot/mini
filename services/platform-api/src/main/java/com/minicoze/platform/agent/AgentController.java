package com.minicoze.platform.agent;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService service;
    public AgentController(AgentService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) AgentResponse create(@Valid @RequestBody AgentRequest request) { return service.create(request); }
    @GetMapping List<AgentResponse> list() { return service.list(); }
    @GetMapping("/{id}") AgentResponse get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/{id}") AgentResponse update(@PathVariable UUID id, @Valid @RequestBody AgentRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id) { service.delete(id); }
}
