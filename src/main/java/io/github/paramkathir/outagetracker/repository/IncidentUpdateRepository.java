package io.github.paramkathir.outagetracker.repository;

import io.github.paramkathir.outagetracker.domain.IncidentUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentUpdateRepository extends JpaRepository<IncidentUpdate, Long> {

    /** Newest first; ties (same instant) broken by insertion order, newest first. */
    List<IncidentUpdate> findByIncidentIdOrderByCreatedAtDescIdDesc(Long incidentId);
}
