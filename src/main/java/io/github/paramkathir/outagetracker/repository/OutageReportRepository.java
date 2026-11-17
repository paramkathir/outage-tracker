package io.github.paramkathir.outagetracker.repository;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.OutageReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutageReportRepository extends JpaRepository<OutageReport, Long> {

    /**
     * Recent unclustered reports inside a bounding box. The box is a cheap pre-filter that
     * can use the (service_type, reported_at) and lat/lng indexes; the exact haversine
     * distance check happens in {@code IncidentClusterer}.
     */
    @Query("""
            select r from OutageReport r
            where r.incident is null
              and r.serviceType = :serviceType
              and r.reportedAt >= :since
              and r.latitude between :minLat and :maxLat
              and r.longitude between :minLng and :maxLng
            """)
    List<OutageReport> findUnclusteredInBox(@Param("serviceType") ServiceType serviceType,
                                            @Param("since") Instant since,
                                            @Param("minLat") double minLat,
                                            @Param("maxLat") double maxLat,
                                            @Param("minLng") double minLng,
                                            @Param("maxLng") double maxLng);

    boolean existsByClientHashAndServiceTypeAndReportedAtAfter(String clientHash, ServiceType serviceType,
                                                               Instant after);

    Page<OutageReport> findByIncidentIdOrderByReportedAtDesc(Long incidentId, Pageable pageable);

    Page<OutageReport> findAllByOrderByReportedAtDesc(Pageable pageable);

    long countByServiceTypeAndReportedAtAfter(ServiceType serviceType, Instant after);

    /** Timestamps only (no entity loading) for an incident's activity chart. */
    @Query("select r.reportedAt from OutageReport r where r.incident.id = :incidentId and r.reportedAt >= :since")
    List<Instant> findReportTimesForIncidentSince(@Param("incidentId") Long incidentId,
                                                  @Param("since") Instant since);

    /** Timestamps of all reports (any service) inside a bounding box, for the "reports near you" chart. */
    @Query("""
            select r.reportedAt from OutageReport r
            where r.reportedAt >= :since
              and r.latitude between :minLat and :maxLat
              and r.longitude between :minLng and :maxLng
            """)
    List<Instant> findReportTimesInBoxSince(@Param("since") Instant since,
                                            @Param("minLat") double minLat,
                                            @Param("maxLat") double maxLat,
                                            @Param("minLng") double minLng,
                                            @Param("maxLng") double maxLng);
}
