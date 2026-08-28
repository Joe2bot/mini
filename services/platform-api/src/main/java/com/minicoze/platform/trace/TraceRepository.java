package com.minicoze.platform.trace;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TraceRepository extends JpaRepository<TraceEntity, UUID> { Optional<TraceEntity> findByRunId(UUID runId); }
