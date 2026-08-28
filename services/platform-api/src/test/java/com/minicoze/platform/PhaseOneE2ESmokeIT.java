package com.minicoze.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="runtime.base-url=http://localhost:8000") @AutoConfigureMockMvc
class PhaseOneE2ESmokeIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;
    @Test void createAgentRunThroughRuntimeAndObserveSseTrace() throws Exception {
        String agentJson=mvc.perform(post("/api/agents").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"E2E\",\"description\":\"Smoke agent\",\"systemPrompt\":\"Be concise\",\"modelProvider\":\"fake\",\"modelName\":\"fake\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID agentId=UUID.fromString(mapper.readTree(agentJson).path("id").asText());
        String runJson=mvc.perform(post("/api/agents/{id}/runs",agentId).contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"stream this\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("queued")).andReturn().getResponse().getContentAsString();
        JsonNode created=mapper.readTree(runJson); UUID runId=UUID.fromString(created.path("run_id").asText()); UUID traceId=UUID.fromString(created.path("trace_id").asText());
        Instant deadline=Instant.now().plus(Duration.ofSeconds(10)); String status="queued";
        while(Instant.now().isBefore(deadline)) {
            String body=mvc.perform(get("/api/runs/{id}",runId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode run=mapper.readTree(body); status=run.path("status").asText(); if("completed".equals(status)) { assertEquals("Fake response: stream this",run.path("output").asText()); break; }
            Thread.sleep(100);
        }
        assertEquals("completed",status);
        mvc.perform(get("/api/runs/{id}/events",runId)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("llm.delta"))).andExpect(content().string(org.hamcrest.Matchers.containsString("run.completed")));
        mvc.perform(get("/api/traces/{id}",traceId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed")).andExpect(jsonPath("$.spans[0].node_type").value("LLM"));
    }
}
