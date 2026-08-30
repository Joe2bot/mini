package com.minicoze.platform.agent;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "agent_tool_bindings", uniqueConstraints = @UniqueConstraint(columnNames = {"agentId", "toolId"}))
public class AgentToolBindingEntity {
    @Id private UUID id;
    @Column(nullable=false) private UUID agentId;
    @Column(nullable=false) private UUID toolId;
    @Column(nullable=false) private boolean enabled;
    protected AgentToolBindingEntity() {}
    public AgentToolBindingEntity(UUID agentId, UUID toolId) { this.id=UUID.randomUUID(); this.agentId=agentId; this.toolId=toolId; this.enabled=true; }
    public UUID getAgentId(){return agentId;} public UUID getToolId(){return toolId;} public boolean isEnabled(){return enabled;}
}
