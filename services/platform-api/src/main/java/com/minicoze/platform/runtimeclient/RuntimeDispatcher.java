package com.minicoze.platform.runtimeclient;

import com.minicoze.platform.run.RunEventService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class RuntimeDispatcher {
    private final WebClient client; private final RunEventService events;
    public RuntimeDispatcher(@Value("${runtime.base-url}") String baseUrl, RunEventService events) { this.client=WebClient.builder().baseUrl(baseUrl).build(); this.events=events; }
    @Async public void dispatch(RuntimeStartRequest request) {
        try {
            client.post().uri("/internal/v1/runs").contentType(MediaType.APPLICATION_JSON).bodyValue(request).retrieve().toBodilessEntity().block();
            client.get().uri("/internal/v1/runs/{id}/events",request.runId()).accept(MediaType.TEXT_EVENT_STREAM).retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<RuntimeEvent>>() {})
                .filter(sse -> sse.data()!=null).doOnNext(sse -> events.accept(sse.data())).blockLast();
        } catch (Exception ex) { events.failRuntime(request.runId(), ex.getClass().getSimpleName()); }
    }
}
