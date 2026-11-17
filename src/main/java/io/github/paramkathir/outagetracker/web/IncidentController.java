package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.core.ServiceType;
import io.github.paramkathir.outagetracker.service.IncidentService;
import io.github.paramkathir.outagetracker.service.IncidentStreamService;
import io.github.paramkathir.outagetracker.web.dto.IncidentView;
import io.github.paramkathir.outagetracker.web.dto.Responses.IncidentDetail;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentStreamService streamService;

    public IncidentController(IncidentService incidentService, IncidentStreamService streamService) {
        this.incidentService = incidentService;
        this.streamService = streamService;
    }

    @GetMapping
    public List<IncidentView> list(@RequestParam(defaultValue = "ACTIVE") IncidentService.Scope scope,
                                   @RequestParam(required = false) ServiceType serviceType) {
        return incidentService.list(scope, serviceType);
    }

    @GetMapping("/{id}")
    public IncidentDetail get(@PathVariable long id,
                              @RequestParam(defaultValue = "20") int reports) {
        return incidentService.get(id, reports);
    }

    /** Live feed of incident changes (Server-Sent Events). */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.connect();
    }
}
