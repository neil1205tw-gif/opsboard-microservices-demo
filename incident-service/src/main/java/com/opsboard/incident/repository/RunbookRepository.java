package com.opsboard.incident.repository;

import com.opsboard.incident.entity.Runbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunbookRepository extends JpaRepository<Runbook, Long> {

    List<Runbook> findAllByServiceName(String serviceName);

    boolean existsByServiceName(String serviceName);
}
