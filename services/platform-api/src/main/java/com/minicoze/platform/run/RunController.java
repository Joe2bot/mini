package com.minicoze.platform.run;

import com.minicoze.platform.runtimeclient.RuntimeDispatcher;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class RunController {
    private final RunService service; private final RuntimeDispatcher dispatcher; private final RunEventRepository events; private final RunEventHub hub;
    public RunController(RunService service, RuntimeDispatcher dispatcher, RunEventRepository events, RunEventHub hub){this.service=service;this.dispatcher=dispatcher;this.events=events;this.hub=hub;}
    @PostMapping("/agents/{agentId}/runs")
    public ResponseEntity<RunStartResponse> create(@PathVariable UUID agentId,@Valid @RequestBody CreateRunRequest request){RunCreated created=service.createAgentRun(agentId,request.input()); dispatcher.dispatch(created.runtimeRequest()); return ResponseEntity.accepted().body(created.response());}
    @GetMapping("/runs/{runId}") public RunResponse get(@PathVariable UUID runId){return service.get(runId);}
    @GetMapping(value="/runs/{runId}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID runId) throws IOException {
        RunResponse run=service.get(runId); SseEmitter emitter=hub.register(runId);
        for(RunEventEntity event:events.findByRunIdOrderById(runId)) emitter.send(SseEmitter.event().id(event.getId().toString()).name(event.getEventType()).data(event.getPayload(),MediaType.APPLICATION_JSON));
        if(run.status()==RunStatus.completed || run.status()==RunStatus.failed) emitter.complete();
        return emitter;
    }
}
