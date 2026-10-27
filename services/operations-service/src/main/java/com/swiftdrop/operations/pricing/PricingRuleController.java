package com.swiftdrop.operations.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/admin/pricing-rules")
@PreAuthorize("hasRole('DISPATCHER')")
public class PricingRuleController {

    private final PricingRuleRepository repository;
    private final PricingAuditService auditService;

    public PricingRuleController(PricingRuleRepository repository, PricingAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<PricingRuleResponse> list() {
        return repository.findAll().stream().map(PricingRuleResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRuleResponse create(@Valid @RequestBody PricingRuleRequest request) {
        request.validate();
        PricingRule rule = repository.save(request.toEntity());
        auditService.record("PRICING_RULE_CREATED", rule.getId().toString());
        return PricingRuleResponse.from(rule);
    }

    @GetMapping("/{ruleId}")
    public PricingRuleResponse get(@PathVariable UUID ruleId) {
        return PricingRuleResponse.from(find(ruleId));
    }

    @PutMapping("/{ruleId}")
    public PricingRuleResponse update(@PathVariable UUID ruleId, @Valid @RequestBody PricingRuleRequest request) {
        request.validate();
        PricingRule rule = find(ruleId);
        rule.replace(request.kind(), request.key(), request.minimumWeightKg(), request.maximumWeightKg(),
                request.chargeLkr(), request.active() == null || request.active());
        PricingRule saved = repository.save(rule);
        auditService.record("PRICING_RULE_UPDATED", saved.getId().toString());
        return PricingRuleResponse.from(saved);
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ruleId) {
        PricingRule rule = find(ruleId);
        repository.delete(rule);
        auditService.record("PRICING_RULE_DELETED", rule.getId().toString());
    }

    private PricingRule find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new PricingRuleNotFoundException(id));
    }

    public record PricingRuleRequest(
            PricingRule.Kind kind,
            @NotBlank String key,
            BigDecimal minimumWeightKg,
            BigDecimal maximumWeightKg,
            @DecimalMin("0.00") BigDecimal chargeLkr,
            Boolean active) {
        void validate() {
            if (kind == null || chargeLkr == null) {
                throw new IllegalArgumentException("kind and chargeLkr are required");
            }
            if (kind == PricingRule.Kind.WEIGHT_BAND
                    && (minimumWeightKg == null || maximumWeightKg == null
                    || minimumWeightKg.signum() < 0
                    || maximumWeightKg.compareTo(minimumWeightKg) <= 0)) {
                throw new IllegalArgumentException("weight rules require valid minimum and maximum bounds");
            }
            if (kind == PricingRule.Kind.SIZE && (minimumWeightKg != null || maximumWeightKg != null)) {
                throw new IllegalArgumentException("size rules must not define weight bounds");
            }
        }
        PricingRule toEntity() {
            return new PricingRule(kind, key, minimumWeightKg, maximumWeightKg, chargeLkr,
                    active == null || active);
        }
    }

    public record PricingRuleResponse(
            UUID id, PricingRule.Kind kind, String key, BigDecimal minimumWeightKg,
            BigDecimal maximumWeightKg, BigDecimal chargeLkr, boolean active,
            Instant createdAt, Instant updatedAt) {
        static PricingRuleResponse from(PricingRule rule) {
            return new PricingRuleResponse(rule.getId(), rule.getKind(), rule.getKey(),
                    rule.getMinimumWeightKg(), rule.getMaximumWeightKg(), rule.getChargeLkr(),
                    rule.isActive(), rule.getCreatedAt(), rule.getUpdatedAt());
        }
    }
}
