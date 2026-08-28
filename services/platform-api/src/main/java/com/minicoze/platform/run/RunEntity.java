package com.minicoze.platform.run;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "runs")
public class RunEntity {
    @Id private UUID id;
    @Column(nullable=false, unique=true) private UUID traceId;
    @Column(nullable=false) private UUID agentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RunStatus status;
    @Column(nullable=false, columnDefinition="TEXT") private String input;
    @Column(columnDefinition="TEXT") private String output;
    @Column(nullable=false, columnDefinition="TEXT") private String configurationSnapshot;
    private Instant startedAt; private Instant endedAt;
    private String errorCode;
    @Column(columnDefinition="TEXT") private String errorMessage;
    protected RunEntity() {}
    public RunEntity(UUID id, UUID traceId, UUID agentId, String input, String snapshot) { this.id=id; this.traceId=traceId; this.agentId=agentId; this.input=input; this.configurationSnapshot=snapshot; this.status=RunStatus.queued; }
    public void start(){ if(status==RunStatus.queued){status=RunStatus.running;startedAt=Instant.now();} }
    public void complete(String result){ if(status==RunStatus.running || status==RunStatus.queued){status=RunStatus.completed;output=result;endedAt=Instant.now();} }
    public void fail(String code,String message){ if(status==RunStatus.queued || status==RunStatus.running){status=RunStatus.failed;errorCode=code;errorMessage=message;endedAt=Instant.now();} }
    public UUID getId(){return id;} public UUID getTraceId(){return traceId;} public UUID getAgentId(){return agentId;} public RunStatus getStatus(){return status;} public String getInput(){return input;} public String getOutput(){return output;} public String getConfigurationSnapshot(){return configurationSnapshot;} public Instant getStartedAt(){return startedAt;} public Instant getEndedAt(){return endedAt;} public String getErrorCode(){return errorCode;} public String getErrorMessage(){return errorMessage;}
}
