package io.github.paramkathir.outagetracker.web.dto;

import io.github.paramkathir.outagetracker.domain.IncidentUpdate;

import java.time.Instant;

public record UpdateView(long id, IncidentUpdate.Kind kind, String title, String body, Instant createdAt) {

    public static UpdateView from(IncidentUpdate u) {
        return new UpdateView(u.getId(), u.getKind(), u.getTitle(), u.getBody(), u.getCreatedAt());
    }
}
