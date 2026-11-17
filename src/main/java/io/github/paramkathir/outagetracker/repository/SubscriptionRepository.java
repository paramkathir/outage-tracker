package io.github.paramkathir.outagetracker.repository;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("select s from Subscription s where s.serviceType is null or s.serviceType = :serviceType")
    List<Subscription> findCovering(@Param("serviceType") ServiceType serviceType);

    Optional<Subscription> findByUnsubscribeToken(String token);
}
