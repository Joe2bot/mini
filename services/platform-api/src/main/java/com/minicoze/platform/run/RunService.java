package com.minicoze.platform.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minicoze.platform.agent.AgentEntity;
import com.minicoze.platform.agent.AgentService;
import com.minicoze.platform.common.ApiException;
import com.minicoze.platform.runtimeclient.RuntimeStartRequest;
import com.minicoze.platform.trace.TraceEntity;
import com.minicoze.platform.trace.TraceRepository;
import com.minicoze.platform.tool.ToolEntity;
import com.minicoze.platform.tool.ToolService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunService {
    private final RunRepository runs; private final TraceRepository traces; private final AgentService agents; private final ToolService tools; private final ObjectMapper mapper;
    public RunService(RunRepository runs, TraceRepository traces, AgentService agents, ToolService tools, ObjectMapper mapper){this.runs=runs;this.traces=traces;this.agents=agents;this.tools=tools;this.mapper=mapper;}
    @Transactional public RunCreated createAgentRun(UUID agentId, String input) {
        AgentEntity agent=agents.getEntity(agentId); UUID runId=UUID.randomUUID(); UUID traceId=UUID.randomUUID();
        ObjectNode agentSnapshot=mapper.createObjectNode().put("id",agent.getId().toString()).put("name",agent.getName()).put("system_prompt",agent.getSystemPrompt()).put("model_provider",agent.getModelProvider()).put("model_name",agent.getModelName());
        if(agent.getTemperature()!=null) agentSnapshot.put("temperature",agent.getTemperature()); if(agent.getMaxTokens()!=null) agentSnapshot.put("max_tokens",agent.getMaxTokens());
        ObjectNode snapshot=mapper.createObjectNode().set("agent",agentSnapshot); ArrayNode allowedTools=snapshot.putArray("tools");
        for (ToolEntity tool : tools.allowedForAgent(agentId)) {
            ObjectNode definition=allowedTools.addObject(); definition.put("id",tool.getId().toString()).put("tool_name",tool.getToolName()).put("description",tool.getDescription());
            try { definition.set("input_schema", mapper.readTree(tool.getInputSchema())); } catch(Exception e) { throw new IllegalStateException(e); }
            definition.put("source_type",tool.getSourceType().value()).put("source_id",tool.getSourceId()).put("enabled",tool.isEnabled());
        }
        String snapshotJson; try { snapshotJson=mapper.writeValueAsString(snapshot); } catch(Exception e){throw new IllegalStateException(e);}
        runs.save(new RunEntity(runId,traceId,agentId,input,snapshotJson)); traces.save(new TraceEntity(traceId,runId,agentId));
        RuntimeStartRequest request=new RuntimeStartRequest(runId,traceId,"agent",agentId,null,input,snapshot,new RuntimeStartRequest.Limits(10,30,300));
        return new RunCreated(new RunStartResponse(runId,traceId,RunStatus.queued),request);
    }
    @Transactional(readOnly=true) public RunResponse get(UUID id){return RunResponse.from(runs.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"RUN_NOT_FOUND","Run not found")));}
}
