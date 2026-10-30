package com.swiftdrop.operations.courier.profile;

import com.swiftdrop.operations.courier.execution.DeliveryProofRepository;
import com.swiftdrop.operations.courier.execution.client.ParcelResponse;
import com.swiftdrop.operations.courier.execution.client.ParcelServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierProfileOperationsTest {
    @Mock CourierProfileService profiles;
    @Mock ParcelServiceClient parcels;
    @Mock DeliveryProofRepository proofs;
    @InjectMocks CourierProfileOperations operations;

    @Test
    void failureEndpointRejectsParcelOwnedByAnotherCourier() {
        UUID parcelId = UUID.randomUUID();
        when(parcels.getParcel(parcelId)).thenReturn(new ParcelResponse(
            parcelId, "SD-1", "OUT_FOR_DELIVERY", "customer", null, "other-courier",
            null, null, 1.0, "SMALL", 100.0, 0, null, "Recipient", null, null, null, 1L, null, null
        ));

        assertThatThrownBy(() -> operations.failDelivery("courier-1", parcelId,
            new FailedDeliveryRequest(FailureReasonCode.RECIPIENT_UNAVAILABLE, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN");
    }
}
