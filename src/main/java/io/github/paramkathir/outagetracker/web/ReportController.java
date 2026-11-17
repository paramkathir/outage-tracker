package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.service.ReportService;
import io.github.paramkathir.outagetracker.web.dto.ReportView;
import io.github.paramkathir.outagetracker.web.dto.Requests;
import io.github.paramkathir.outagetracker.web.dto.Responses.ReportResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<ReportResult> submit(@Valid @RequestBody Requests.CreateReport request,
                                               HttpServletRequest http) {
        ReportResult result = reportService.submit(request, http.getRemoteAddr());
        return ResponseEntity
                .created(URI.create("/api/reports/" + result.report().id()))
                .body(result);
    }

    @GetMapping("/recent")
    public List<ReportView> recent(@RequestParam(defaultValue = "50") int limit) {
        return reportService.recent(limit);
    }
}
