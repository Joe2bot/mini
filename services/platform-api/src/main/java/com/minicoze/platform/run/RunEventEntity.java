package com.minicoze.platform.run;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="run_events")
public class RunEventEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private UUID runId;
    @Column(nullable=false) private String eventType;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(nullable=false) private Instant createdAt;
    protected RunEventEntity() {}
    public RunEventEntity(UUID runId,String eventType,String payload){this.runId=runId;this.eventType=eventType;this.payload=payload;this.createdAt=Instant.now();}
    public Long getId(){return id;} public UUID getRunId(){return runId;} public String getEventType(){return eventType;} public String getPayload(){return payload;} public Instant getCreatedAt(){return createdAt;}
}
