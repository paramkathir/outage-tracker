package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.service.IncidentService;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.github.paramkathir.outagetracker.web.dto.Requests;
import io.github.paramkathir.outagetracker.web.dto.UpdateView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator endpoints. Protected by {@link AdminKeyInterceptor} (X-Admin-Key header). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final IncidentService incidentService;

    public AdminController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping("/incidents/{id}/resolve")
    public IncidentView resolve(@PathVariable long id,
                                @Valid @RequestBody(required = false) Requests.ResolveIncident body) {
        return incidentService.resolve(id, body == null ? null : body.note());
    }

    /** Adds an operator update to the incident's public timeline. */
    @PostMapping("/incidents/{id}/updates")
    @ResponseStatus(HttpStatus.CREATED)
    public UpdateView postUpdate(@PathVariable long id, @Valid @RequestBody Requests.PostUpdate body) {
        return incidentService.postUpdate(id, body.title(), body.body());
    }
}
