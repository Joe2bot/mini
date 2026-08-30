package com.minicoze.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicoze.platform.runtimeclient.RuntimeDispatcher;
import com.minicoze.platform.runtimeclient.RuntimeStartRequest;
import com.minicoze.platform.run.RunEventService;
import com.minicoze.platform.run.RunRepository;
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

@SpringBootTest(classes = {PlatformApiApplication.class, ToolApiIntegrationTest.RuntimeTestConfiguration.class})
@AutoConfigureMockMvc
class ToolApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired RunRepository runs;
    @TestConfiguration static class RuntimeTestConfiguration {
        @Bean @Primary RuntimeDispatcher testRuntimeDispatcher(RunEventService events) { return new RuntimeDispatcher("http://localhost:9999", events) { @Override public void dispatch(RuntimeStartRequest request) {} }; }
    }
    @Test void crudBindAndRunSnapshotContainCompleteToolDefinition() throws Exception {
        String schema="{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"expression\\\":{\\\"type\\\":\\\"string\\\"}},\\\"required\\\":[\\\"expression\\\"],\\\"additionalProperties\\\":false}";
        String tool=mvc.perform(post("/api/tools").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"calculator\",\"description\":\"math\",\"inputSchema\":\""+schema+"\",\"sourceType\":\"native\",\"sourceId\":\"calculator\",\"enabled\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sourceType").value("native")).andReturn().getResponse().getContentAsString();
        UUID toolId=UUID.fromString(mapper.readTree(tool).path("id").asText());
        String agent=mvc.perform(post("/api/agents").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tool Agent\",\"description\":\"test\",\"systemPrompt\":\"system\",\"modelProvider\":\"fake\",\"modelName\":\"fake\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID agentId=UUID.fromString(mapper.readTree(agent).path("id").asText());
        mvc.perform(put("/api/agents/{id}/tools",agentId).contentType(MediaType.APPLICATION_JSON).content("{\"toolIds\":[\""+toolId+"\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("calculator"));
        String created=mvc.perform(post("/api/agents/{id}/runs",agentId).contentType(MediaType.APPLICATION_JSON).content("{\"input\":\"2+2\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID runId=UUID.fromString(mapper.readTree(created).path("run_id").asText());
        JsonNode snapshot=mapper.readTree(runs.findById(runId).orElseThrow().getConfigurationSnapshot());
        org.junit.jupiter.api.Assertions.assertEquals("calculator", snapshot.path("tools").get(0).path("tool_name").asText());
        org.junit.jupiter.api.Assertions.assertEquals("calculator", snapshot.path("tools").get(0).path("source_id").asText());
        mvc.perform(get("/api/agents/{id}/tools",agentId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].inputSchema").value(org.hamcrest.Matchers.containsString("expression")));
    }
}
