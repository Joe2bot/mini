package com.minicoze.platform.run;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunEventRepository extends JpaRepository<RunEventEntity, Long> { List<RunEventEntity> findByRunIdOrderById(UUID runId); }
