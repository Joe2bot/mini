package com.minicoze.platform.run;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RunRepository extends JpaRepository<RunEntity, UUID> {}
