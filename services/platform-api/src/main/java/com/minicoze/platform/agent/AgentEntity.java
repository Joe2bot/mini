package com.minicoze.platform.agent;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class AgentEntity {
    @Id private UUID id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String systemPrompt;
    @Column(nullable = false) private String modelProvider;
    @Column(nullable = false) private String modelName;
    private Double temperature;
    private Integer maxTokens;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected AgentEntity() {}
    public AgentEntity(UUID id, String name, String description, String systemPrompt, String modelProvider, String modelName, Double temperature, Integer maxTokens) {
        this.id = id; this.name = name; this.description = description; this.systemPrompt = systemPrompt;
        this.modelProvider = modelProvider; this.modelName = modelName; this.temperature = temperature; this.maxTokens = maxTokens;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public void update(String name, String description, String systemPrompt, String modelProvider, String modelName, Double temperature, Integer maxTokens) {
        this.name=name; this.description=description; this.systemPrompt=systemPrompt; this.modelProvider=modelProvider; this.modelName=modelName;
        this.temperature=temperature; this.maxTokens=maxTokens; this.updatedAt=Instant.now();
    }
    public UUID getId(){ return id; } public String getName(){ return name; } public String getDescription(){ return description; }
    public String getSystemPrompt(){ return systemPrompt; } public String getModelProvider(){ return modelProvider; } public String getModelName(){ return modelName; }
    public Double getTemperature(){ return temperature; } public Integer getMaxTokens(){ return maxTokens; } public Instant getCreatedAt(){ return createdAt; } public Instant getUpdatedAt(){ return updatedAt; }
}
