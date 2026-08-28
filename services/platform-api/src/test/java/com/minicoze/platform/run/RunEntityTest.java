package com.minicoze.platform.run;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunEntityTest {
    @Test void transitionsFromQueuedToCompleted() { RunEntity run=new RunEntity(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"hello","{}"); assertEquals(RunStatus.queued,run.getStatus()); run.start(); run.complete("answer"); assertEquals(RunStatus.completed,run.getStatus()); assertEquals("answer",run.getOutput()); assertNotNull(run.getStartedAt()); assertNotNull(run.getEndedAt()); }
    @Test void transitionsFromQueuedToFailed() { RunEntity run=new RunEntity(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"hello","{}"); run.fail("RUNTIME_UNAVAILABLE","unavailable"); assertEquals(RunStatus.failed,run.getStatus()); assertEquals("RUNTIME_UNAVAILABLE",run.getErrorCode()); }
}
