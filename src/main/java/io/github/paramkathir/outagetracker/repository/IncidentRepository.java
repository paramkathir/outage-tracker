package io.github.paramkathir.outagetracker.repository;

import io.github.paramkathir.outagetracker.core.IncidentStatus;
import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByServiceTypeAndStatusIn(ServiceType serviceType, Collection<IncidentStatus> statuses);

    List<Incident> findByStatusInOrderByLastReportedAtDesc(Collection<IncidentStatus> statuses);

    List<Incident> findByStatusInAndServiceTypeOrderByLastReportedAtDesc(Collection<IncidentStatus> statuses,
                                                                         ServiceType serviceType);

    List<Incident> findByStatusInAndLastReportedAtLessThanEqual(Collection<IncidentStatus> statuses, Instant cutoff);

    long countByServiceTypeAndStatus(ServiceType serviceType, IncidentStatus status);
}
