package io.github.paramkathir.outagetracker.service;

import io.github.paramkathir.outagetracker.core.ServiceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serialises writes that touch incidents of the same service type.
 *
 * <p>Without this, two reports arriving at the same moment for the same neighbourhood could
 * each see "2 unclustered neighbours", and both open a new incident. Locking per service type
 * keeps clustering correct while still letting INTERNET and TV reports proceed in parallel.</p>
 *
 * <p>The lock must be held <em>around</em> the transaction (acquire, begin, commit, release);
 * otherwise a second thread could read before the first commits. This works for a single
 * instance. Running several instances would call for a database-level lock instead
 * (e.g. PostgreSQL {@code pg_advisory_xact_lock}); see README "Scaling further".</p>
 */
@Component
public class ServiceTypeLocks {

    private final Map<ServiceType, ReentrantLock> locks = new EnumMap<>(ServiceType.class);

    public ServiceTypeLocks() {
        for (ServiceType type : ServiceType.values()) {
            locks.put(type, new ReentrantLock());
        }
    }

    public <T> T withLock(ServiceType type, Supplier<T> action) {
        ReentrantLock lock = locks.get(type);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
