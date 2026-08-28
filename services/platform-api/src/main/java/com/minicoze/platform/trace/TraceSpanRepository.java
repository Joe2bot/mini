package com.minicoze.platform.trace;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TraceSpanRepository extends JpaRepository<TraceSpanEntity, UUID> { List<TraceSpanEntity> findByTraceIdOrderByStartedAt(UUID traceId); }
