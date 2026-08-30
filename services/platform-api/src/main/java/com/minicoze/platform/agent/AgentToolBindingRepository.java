package com.minicoze.platform.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolBindingRepository extends JpaRepository<AgentToolBindingEntity, UUID> {
    List<AgentToolBindingEntity> findByAgentIdAndEnabledTrue(UUID agentId);
    void deleteByAgentId(UUID agentId);
    void deleteByToolId(UUID toolId);
}
