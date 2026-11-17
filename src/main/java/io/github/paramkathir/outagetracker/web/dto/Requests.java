package io.github.paramkathir.outagetracker.web.dto;

import io.github.paramkathir.outagetracker.core.ServiceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Inbound request bodies. */
public final class Requests {

    private Requests() {
    }

    public record CreateReport(
            @NotNull ServiceType serviceType,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @Pattern(regexp = "^\\d{5}$", message = "must be a 5-digit ZIP code") String zipCode,
            @Size(max = 500) String description) {
    }

    public record CreateSubscription(
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @NotNull @DecimalMin("0.5") @DecimalMax("50.0") Double radiusKm,
            ServiceType serviceType,
            @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https:// URL") String webhookUrl) {
    }

    public record ResolveIncident(@Size(max = 500) String note) {
    }

    public record PostUpdate(@Size(max = 120) String title, @NotBlank @Size(max = 1000) String body) {
    }
}
