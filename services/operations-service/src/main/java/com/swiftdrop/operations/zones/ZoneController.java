package com.swiftdrop.operations.zones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/zones")
@PreAuthorize("hasRole('DISPATCHER')")
public class ZoneController {

    private final ZoneRepository repository;
    private final ZoneAuditService auditService;

    public ZoneController(ZoneRepository repository, ZoneAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<ZoneResponse> list() {
        return repository.findAll().stream().map(ZoneResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneResponse create(@Valid @RequestBody ZoneRequest request) {
        request.validateDistricts();
        Zone zone = repository.save(new Zone(request.name(), request.districts(), request.served(), request.baseFeeLkr()));
        auditService.record("ZONE_CREATED", zone.getId().toString());
        return ZoneResponse.from(zone);
    }

    @GetMapping("/{zoneId}")
    public ZoneResponse get(@PathVariable UUID zoneId) {
        return ZoneResponse.from(find(zoneId));
    }

    @PutMapping("/{zoneId}")
    public ZoneResponse update(@PathVariable UUID zoneId, @Valid @RequestBody ZoneRequest request) {
        request.validateDistricts();
        Zone zone = find(zoneId);
        zone.replace(request.name(), request.districts(), request.served(), request.baseFeeLkr());
        Zone saved = repository.save(zone);
        auditService.record("ZONE_UPDATED", saved.getId().toString());
        return ZoneResponse.from(saved);
    }

    @DeleteMapping("/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID zoneId) {
        Zone zone = find(zoneId);
        repository.delete(zone);
        auditService.record("ZONE_DELETED", zone.getId().toString());
    }

    private Zone find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ZoneNotFoundException(id));
    }

    public record ZoneRequest(
            @NotBlank String name,
            @NotEmpty Set<String> districts,
            boolean served,
            @DecimalMin("0.00") BigDecimal baseFeeLkr) {
        void validateDistricts() { Districts.validate(districts); }
    }

    public record ZoneResponse(
            UUID id, String name, Set<String> districts, boolean served,
            BigDecimal baseFeeLkr, Instant createdAt, Instant updatedAt) {
        static ZoneResponse from(Zone zone) {
            return new ZoneResponse(zone.getId(), zone.getName(), zone.getDistricts(), zone.isServed(),
                    zone.getBaseFeeLkr(), zone.getCreatedAt(), zone.getUpdatedAt());
        }
    }
}
