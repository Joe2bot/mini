package com.minicoze.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minicoze.platform.runtimeclient.*;
import com.minicoze.platform.run.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {PlatformApiApplication.class, RunApiIntegrationTest.RuntimeTestConfiguration.class}) @AutoConfigureMockMvc
class RunApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired RunEventService eventService; @Autowired RunService runService;
    @TestConfiguration static class RuntimeTestConfiguration {
        @Bean @Primary RuntimeDispatcher testRuntimeDispatcher(RunEventService events) {
            return new RuntimeDispatcher("http://localhost:9999", events) { @Override public void dispatch(RuntimeStartRequest request) { } };
        }
    }
    @Test void createsAsyncRunPersistsTraceAndAcceptsRuntimeSseEvents() throws Exception {
        String agent=mvc.perform(post("/api/agents").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test\",\"description\":\"Test agent\",\"systemPrompt\":\"system\",\"modelProvider\":\"fake\",\"modelName\":\"fake\",\"temperature\":0.2,\"maxTokens\":64}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID agentId=UUID.fromString(mapper.readTree(agent).path("id").asText());
        String created=mvc.perform(post("/api/agents/{id}/runs",agentId).contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"hello\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("queued")).andReturn().getResponse().getContentAsString();
        UUID runId=UUID.fromString(mapper.readTree(created).path("run_id").asText()); UUID traceId=UUID.fromString(mapper.readTree(created).path("trace_id").asText());
        ObjectNode started=mapper.createObjectNode().put("status","running"); eventService.accept(new RuntimeEvent(1,"run.started",runId,traceId,Instant.now(),started));
        UUID spanId=UUID.randomUUID(); ObjectNode span=mapper.createObjectNode().put("span_id",spanId.toString()).put("span_type","LLM").put("name","agent.llm").set("input",mapper.createObjectNode().put("user_input","hello")); eventService.accept(new RuntimeEvent(2,"trace.span.started",runId,traceId,Instant.now(),span));
        ObjectNode done=mapper.createObjectNode().put("output","hello back"); eventService.accept(new RuntimeEvent(3,"run.completed",runId,traceId,Instant.now(),done));
        mvc.perform(get("/api/runs/{id}",runId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed")).andExpect(jsonPath("$.output").value("hello back"));
        mvc.perform(get("/api/traces/{id}",traceId)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed")).andExpect(jsonPath("$.spans[0].node_type").value("LLM"));
        mvc.perform(get("/api/runs/{id}/events",runId)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("run.completed")));
    }
    @Test void persistsToolEventsAsToolTraceSpan() throws Exception {
        String agent=mvc.perform(post("/api/agents").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Trace Tool\",\"description\":\"Test agent\",\"systemPrompt\":\"system\",\"modelProvider\":\"fake\",\"modelName\":\"fake\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID agentId=UUID.fromString(mapper.readTree(agent).path("id").asText());
        String created=mvc.perform(post("/api/agents/{id}/runs",agentId).contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"hello\"}"))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID runId=UUID.fromString(mapper.readTree(created).path("run_id").asText()); UUID traceId=UUID.fromString(mapper.readTree(created).path("trace_id").asText());
        UUID toolSpan=UUID.randomUUID(); ObjectNode start=mapper.createObjectNode().put("span_id",toolSpan.toString()).put("span_type","TOOL").put("name","calculator").set("input",mapper.createObjectNode().put("tool_call_id","call-1"));
        eventService.accept(new RuntimeEvent(1,"tool.started",runId,traceId,Instant.now(),mapper.createObjectNode().put("tool_name","calculator")));
        eventService.accept(new RuntimeEvent(2,"trace.span.started",runId,traceId,Instant.now(),start));
        eventService.accept(new RuntimeEvent(3,"tool.completed",runId,traceId,Instant.now(),mapper.createObjectNode().put("tool_name","calculator").set("output",mapper.createObjectNode().put("result",4))));
        eventService.accept(new RuntimeEvent(4,"trace.span.completed",runId,traceId,Instant.now(),mapper.createObjectNode().put("span_id",toolSpan.toString()).set("output",mapper.createObjectNode().put("result",4))));
        mvc.perform(get("/api/traces/{id}",traceId)).andExpect(status().isOk()).andExpect(jsonPath("$.spans[0].node_type").value("TOOL")).andExpect(jsonPath("$.spans[0].name").value("calculator"));
        mvc.perform(get("/api/runs/{id}/events",runId)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("tool.started"))).andExpect(content().string(org.hamcrest.Matchers.containsString("tool.completed")));
    }
}
