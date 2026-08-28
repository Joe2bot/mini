package com.minicoze.platform.trace;

import com.minicoze.platform.run.RunStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="traces")
public class TraceEntity {
    @Id private UUID id;
    @Column(nullable=false,unique=true) private UUID runId;
    @Column(nullable=false) private UUID agentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RunStatus status;
    @Column(nullable=false) private Instant startedAt;
    private Instant endedAt; private Long totalLatencyMs;
    protected TraceEntity() {}
    public TraceEntity(UUID id,UUID runId,UUID agentId){this.id=id;this.runId=runId;this.agentId=agentId;this.status=RunStatus.queued;this.startedAt=Instant.now();}
    public void setStatus(RunStatus status){this.status=status;if(status==RunStatus.completed||status==RunStatus.failed){endedAt=Instant.now();totalLatencyMs=endedAt.toEpochMilli()-startedAt.toEpochMilli();}}
    public UUID getId(){return id;} public UUID getRunId(){return runId;} public UUID getAgentId(){return agentId;} public RunStatus getStatus(){return status;} public Instant getStartedAt(){return startedAt;} public Instant getEndedAt(){return endedAt;} public Long getTotalLatencyMs(){return totalLatencyMs;}
}
