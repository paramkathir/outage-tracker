package io.github.paramkathir.outagetracker.web;

import io.github.paramkathir.outagetracker.service.LocationService;
import io.github.paramkathir.outagetracker.web.dto.Responses.LocationStatus;
import io.github.paramkathir.outagetracker.web.dto.Responses.ZipView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public "what's the status where I am?" lookups. Parameter constraints are enforced by Spring MVC's
 * built-in method validation (400 problem+json on failure).
 */
@RestController
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/api/locations/{zip}")
    public LocationStatus byZip(@PathVariable @Pattern(regexp = "^\\d{5}$", message = "must be a 5-digit ZIP code")
                                String zip) {
        return locationService.forZip(zip);
    }

    @GetMapping("/api/locations")
    public LocationStatus byPoint(@RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
                                  @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng) {
        return locationService.forPoint(lat, lng);
    }

    @GetMapping("/api/zips")
    public List<ZipView> zips() {
        return locationService.supportedZips();
    }
}
