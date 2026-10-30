package com.swiftdrop.parcel.parcel;

import com.swiftdrop.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ParcelQueryServiceImpl implements ParcelQueryService {

    private final ParcelRepository parcelRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public ParcelQueryServiceImpl(ParcelRepository parcelRepository, StatusHistoryRepository statusHistoryRepository) {
        this.parcelRepository = parcelRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Override
    public Optional<ParcelResponse> findByTrackingNumber(String trackingNumber) {
        return parcelRepository.findByTrackingNumber(trackingNumber).map(ParcelResponse::from);
    }

    @Override
    public ParcelResponse getAccessibleParcel(String parcelId, CurrentUser caller) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found with id: " + parcelId));
        if (!canSee(parcel, caller)) {
            throw new AccessDeniedException("Access denied: you may not view this parcel");
        }
        return ParcelResponse.from(parcel);
    }

    @Override
    public List<StatusHistoryView> findStatusHistory(String parcelId) {
        return statusHistoryRepository.findByParcelIdOrderByCreatedAtAsc(parcelId).stream()
                .map(StatusHistoryView::from)
                .toList();
    }

    private static boolean canSee(Parcel parcel, CurrentUser caller) {
        if (caller == null) {
            return false;
        }
        if (caller.isAdmin() || caller.isDispatcher()) {
            return true;
        }
        String userId = caller.userId();
        if (caller.isCourier() && userId != null && Objects.equals(userId, parcel.getCourierId())) {
            return true;
        }
        return caller.isCustomer() && userId != null && Objects.equals(userId, parcel.getCustomerId());
    }
}
