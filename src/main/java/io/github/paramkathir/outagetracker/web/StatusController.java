package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.service.StatusService;
import io.github.paramkathir.outagetracker.web.dto.Responses.StatusSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/api/status")
    public StatusSummary status() {
        return statusService.summary();
    }
}
