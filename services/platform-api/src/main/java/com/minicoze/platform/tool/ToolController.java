package com.minicoze.platform.tool;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final ToolService service;
    public ToolController(ToolService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) ToolResponse create(@Valid @RequestBody ToolRequest request) { return service.create(request); }
    @GetMapping List<ToolResponse> list() { return service.list(); }
    @GetMapping("/{id}") ToolResponse get(@PathVariable UUID id) { return service.get(id); }
    @PutMapping("/{id}") ToolResponse update(@PathVariable UUID id,@Valid @RequestBody ToolRequest request) { return service.update(id,request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id) { service.delete(id); }
}
