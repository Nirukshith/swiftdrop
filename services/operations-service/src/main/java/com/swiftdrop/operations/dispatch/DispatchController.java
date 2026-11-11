package com.swiftdrop.operations.dispatch;

import com.swiftdrop.operations.dispatch.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/parcels")
    public ResponseEntity<UnassignedParcelsPage> listUnassignedParcels(
        @RequestParam(required = false) String zone,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        UnassignedParcelsPage response = dispatchService.listUnassignedParcels(zone, date, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/parcels/failed")
    public ResponseEntity<FailedParcelsPage> listFailedParcels(
        @RequestParam(required = false) String zone,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        FailedParcelsPage response = dispatchService.listFailedParcels(zone, date, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/parcels/{id}/requeue")
    public ResponseEntity<RequeueResponse> requeueParcel(
        @PathVariable String id,
        @RequestBody(required = false) RequeueRequest request,
        Principal principal
    ) {
        String requeuedBy = (principal != null && principal.getName() != null)
            ? principal.getName()
            : "DISPATCHER";
        String reason = (request != null) ? request.reason() : null;
        RequeueResponse response = dispatchService.requeueParcel(id, reason, requeuedBy);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/couriers")
    public ResponseEntity<List<CourierWorkloadItem>> getCourierWorkloadBoard(
        @RequestParam(required = false) String zone,
        @RequestParam(required = false, defaultValue = "false") boolean availableOnly
    ) {
        List<CourierWorkloadItem> board = dispatchService.getCourierWorkloadBoard(zone, availableOnly);
        return ResponseEntity.ok(board);
    }

    @PostMapping("/assignments")
    public ResponseEntity<AssignmentResult> assignParcel(
        @RequestParam(required = false) Boolean reassign,
        @Valid @RequestBody AssignParcelRequest request,
        Principal principal
    ) {
        String assignedBy = (principal != null && principal.getName() != null)
            ? principal.getName()
            : "DISPATCHER";
        boolean isReassign = Boolean.TRUE.equals(reassign) || Boolean.TRUE.equals(request.reassign());
        AssignParcelRequest effectiveRequest = (request.reassign() != null && request.reassign() == isReassign)
            ? request
            : new AssignParcelRequest(request.parcelId(), request.courierId(), isReassign);
        AssignmentResult result = dispatchService.assignParcel(effectiveRequest, assignedBy);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/assignments/bulk")
    public ResponseEntity<BulkAssignResponse> bulkAssignParcels(
        @Valid @RequestBody BulkAssignRequest request,
        Principal principal
    ) {
        String assignedBy = (principal != null && principal.getName() != null)
            ? principal.getName()
            : "DISPATCHER";
        BulkAssignResponse response = dispatchService.bulkAssignParcels(request, assignedBy);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/couriers/{id}/availability")
    public ResponseEntity<CourierWorkloadItem> updateCourierAvailability(
        @PathVariable String id,
        @Valid @RequestBody CourierAvailabilityUpdateRequest request
    ) {
        CourierWorkloadItem updated = dispatchService.updateCourierAvailability(id, request.available());
        return ResponseEntity.ok(updated);
    }
}
