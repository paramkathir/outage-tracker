package io.github.paramkathir.outagetracker.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Pushes committed incident changes to connected browsers over Server-Sent Events,
 * so the status map updates live without polling.
 */
@Service
public class IncidentStreamService {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onIncidentEvent(IncidentEvent event) {
        broadcast(() -> SseEmitter.event()
                .name("incident")
                .data(event));
    }

    /** Comment frames keep idle connections from being cut by proxies and load balancers. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        broadcast(() -> SseEmitter.event().comment("keep-alive"));
    }

    public int connectedClients() {
        return emitters.size();
    }

    /** Builders are single-use (build() mutates them), so make a fresh one per client. */
    private void broadcast(Supplier<SseEmitter.SseEventBuilder> event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event.get());
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
