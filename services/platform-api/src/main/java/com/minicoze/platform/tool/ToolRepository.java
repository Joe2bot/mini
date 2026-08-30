package com.minicoze.platform.tool;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolRepository extends JpaRepository<ToolEntity, UUID> {}
