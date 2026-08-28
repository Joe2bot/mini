package com.minicoze.platform.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicoze.platform.common.ApiException;
import com.minicoze.platform.runtimeclient.RuntimeEvent;
import com.minicoze.platform.trace.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunEventService {
    private final RunRepository runs; private final RunEventRepository events; private final TraceRepository traces; private final TraceSpanRepository spans; private final ObjectMapper mapper; private final RunEventHub hub;
    public RunEventService(RunRepository runs, RunEventRepository events, TraceRepository traces, TraceSpanRepository spans, ObjectMapper mapper, RunEventHub hub) { this.runs=runs;this.events=events;this.traces=traces;this.spans=spans;this.mapper=mapper;this.hub=hub; }
    @Transactional public void accept(RuntimeEvent event) {
        RunEntity run = runs.findById(event.runId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"RUN_NOT_FOUND","Run not found"));
        TraceEntity trace = traces.findById(run.getTraceId()).orElseThrow();
        apply(run, trace, event);
        RunEventEntity persisted = events.save(new RunEventEntity(run.getId(), event.eventType(), serialize(event)));
        hub.publish(persisted);
    }
    @Transactional public void failRuntime(UUID runId, String message) {
        RunEntity run=runs.findById(runId).orElseThrow(); TraceEntity trace=traces.findById(run.getTraceId()).orElseThrow();
        run.fail("RUNTIME_UNAVAILABLE", message); trace.setStatus(RunStatus.failed);
        RuntimeEvent event=new RuntimeEvent(0,"run.failed",runId,run.getTraceId(),java.time.Instant.now(),mapper.createObjectNode().put("code","RUNTIME_UNAVAILABLE").put("message","Runtime unavailable"));
        RunEventEntity persisted=events.save(new RunEventEntity(runId,event.eventType(),serialize(event))); hub.publish(persisted);
    }
    private void apply(RunEntity run, TraceEntity trace, RuntimeEvent event) {
        JsonNode d=event.data();
        switch(event.eventType()) {
            case "run.started" -> { run.start(); trace.setStatus(RunStatus.running); }
            case "run.completed" -> { run.complete(d.path("output").asText()); trace.setStatus(RunStatus.completed); }
            case "run.failed" -> { run.fail(d.path("code").asText("LLM_REQUEST_FAILED"), d.path("message").asText("Runtime execution failed")); trace.setStatus(RunStatus.failed); }
            case "trace.span.started" -> spans.save(new TraceSpanEntity(UUID.fromString(d.path("span_id").asText()), trace.getId(), d.hasNonNull("parent_span_id") ? UUID.fromString(d.path("parent_span_id").asText()) : null, d.path("span_type").asText("LLM"), d.path("name").asText("LLM"), d.path("input").toString()));
            case "trace.span.completed" -> spans.findById(UUID.fromString(d.path("span_id").asText())).ifPresent(span -> span.complete(d.path("output").toString()));
            case "trace.span.failed" -> spans.findById(UUID.fromString(d.path("span_id").asText())).ifPresent(span -> span.fail(d.path("error").toString()));
            default -> { }
        }
    }
    private String serialize(RuntimeEvent event) { try { return mapper.writeValueAsString(event); } catch(JsonProcessingException ex) { throw new IllegalStateException(ex); } }
}
