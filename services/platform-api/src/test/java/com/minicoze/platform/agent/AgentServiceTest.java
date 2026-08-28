package com.minicoze.platform.agent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AgentServiceTest {
    @Autowired AgentRepository repository;
    private AgentService service;
    @BeforeEach void setup() { service = new AgentService(repository); }
    @Test void createsAgentWithConfiguredModel() {
        AgentResponse result=service.create(new AgentRequest("Researcher","test agent","be useful","fake","fake",0.2,100));
        assertEquals("Researcher",result.name()); assertEquals("fake",result.modelProvider()); assertTrue(repository.existsById(result.id()));
    }
}
