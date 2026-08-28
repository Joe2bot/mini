package com.minicoze.platform.trace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="trace_spans")
public class TraceSpanEntity {
    @Id private UUID id;
    @Column(nullable=false) private UUID traceId;
    private UUID parentSpanId;
    @Column(nullable=false) private String spanType;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String status;
    @Column(columnDefinition="TEXT") private String input;
    @Column(columnDefinition="TEXT") private String output;
    @Column(columnDefinition="TEXT") private String error;
    @Column(nullable=false) private Instant startedAt;
    private Instant endedAt; private Long latencyMs;
    protected TraceSpanEntity() {}
    public TraceSpanEntity(UUID id,UUID traceId,UUID parentSpanId,String spanType,String name,String input){this.id=id;this.traceId=traceId;this.parentSpanId=parentSpanId;this.spanType=spanType;this.name=name;this.input=input;this.status="running";this.startedAt=Instant.now();}
    public void complete(String output){this.status="completed";this.output=output;this.endedAt=Instant.now();this.latencyMs=endedAt.toEpochMilli()-startedAt.toEpochMilli();}
    public void fail(String error){this.status="failed";this.error=error;this.endedAt=Instant.now();this.latencyMs=endedAt.toEpochMilli()-startedAt.toEpochMilli();}
    public UUID getId(){return id;} public UUID getTraceId(){return traceId;} public UUID getParentSpanId(){return parentSpanId;} public String getSpanType(){return spanType;} public String getName(){return name;} public String getStatus(){return status;} public String getInput(){return input;} public String getOutput(){return output;} public String getError(){return error;} public Instant getStartedAt(){return startedAt;} public Instant getEndedAt(){return endedAt;} public Long getLatencyMs(){return latencyMs;}
}
