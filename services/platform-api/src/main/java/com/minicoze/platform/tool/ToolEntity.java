package com.minicoze.platform.tool;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_definitions", uniqueConstraints = @UniqueConstraint(columnNames = "toolName"))
public class ToolEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String toolName;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String inputSchema;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ToolSourceType sourceType;
    @Column(nullable = false, length = 160) private String sourceId;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected ToolEntity() {}
    public ToolEntity(UUID id, String toolName, String description, String inputSchema, ToolSourceType sourceType, String sourceId, boolean enabled) {
        this.id=id; this.toolName=toolName; this.description=description; this.inputSchema=inputSchema;
        this.sourceType=sourceType; this.sourceId=sourceId; this.enabled=enabled; this.createdAt=Instant.now(); this.updatedAt=this.createdAt;
    }
    public void update(String toolName, String description, String inputSchema, ToolSourceType sourceType, String sourceId, boolean enabled) {
        this.toolName=toolName; this.description=description; this.inputSchema=inputSchema; this.sourceType=sourceType; this.sourceId=sourceId; this.enabled=enabled; this.updatedAt=Instant.now();
    }
    public UUID getId(){return id;} public String getToolName(){return toolName;} public String getDescription(){return description;} public String getInputSchema(){return inputSchema;}
    public ToolSourceType getSourceType(){return sourceType;} public String getSourceId(){return sourceId;} public boolean isEnabled(){return enabled;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
