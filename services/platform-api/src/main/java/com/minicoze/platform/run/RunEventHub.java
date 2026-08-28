package com.minicoze.platform.run;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class RunEventHub {
    private final Map<UUID, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    public SseEmitter register(UUID runId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter)); emitter.onTimeout(() -> remove(runId, emitter));
        return emitter;
    }
    public void publish(RunEventEntity event) {
        for (SseEmitter emitter : emitters.getOrDefault(event.getRunId(), Set.of())) {
            try { emitter.send(SseEmitter.event().id(event.getId().toString()).name(event.getEventType()).data(event.getPayload(), MediaType.APPLICATION_JSON)); }
            catch (IOException ex) { remove(event.getRunId(), emitter); }
        }
    }
    private void remove(UUID runId, SseEmitter emitter) { Optional.ofNullable(emitters.get(runId)).ifPresent(set -> set.remove(emitter)); }
}
