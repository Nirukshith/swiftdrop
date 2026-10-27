package com.swiftdrop.integration.merchant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantOrderService {

    private static final String CREATE_OPERATION = "CREATE_ORDER";
    private static final String CANCEL_OPERATION = "CANCEL_ORDER";

    private final MerchantRepository merchantRepository;
    private final MerchantOrderRepository orderRepository;
    private final MerchantOrderIdempotencyStore idempotencyStore;
    private final IdempotencyRequestHasher requestHasher;
    private final PricingQuoteClient pricingQuoteClient;
    private final ParcelClient parcelClient;

    public MerchantOrderService(MerchantRepository merchantRepository,
                                MerchantOrderRepository orderRepository,
                                MerchantOrderIdempotencyStore idempotencyStore,
                                IdempotencyRequestHasher requestHasher,
                                PricingQuoteClient pricingQuoteClient,
                                ParcelClient parcelClient) {
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.idempotencyStore = idempotencyStore;
        this.requestHasher = requestHasher;
        this.pricingQuoteClient = pricingQuoteClient;
        this.parcelClient = parcelClient;
    }

    @Transactional
    public MerchantOrderIdempotencyStore.StoredResponse create(UUID merchantId, String idempotencyKey,
                                                               CreateMerchantOrderRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Merchant credentials are invalid, inactive, or not approved for ordering"));
        if (!merchant.getStatus().canPlaceOrders()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Merchant credentials are invalid, inactive, or not approved for ordering");
        }

        String hash = requestHasher.hash(CREATE_OPERATION, request);
        MerchantOrderIdempotencyStore.StoredResponse replay =
                idempotencyStore.reserveOrReplay(merchantId, CREATE_OPERATION, idempotencyKey, hash);
        if (replay != null) {
            return replay;
        }

        if (orderRepository.existsByMerchantIdAndExternalReference(merchantId, request.externalReference())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "External reference has already been used by this merchant");
        }

        try {
            BigDecimal quote = pricingQuoteClient.quote(request);
            ParcelClient.InternalParcelResponse parcel = parcelClient.createParcel(merchantId, request, quote,
                    requestHasher.downstreamKey(merchantId, CREATE_OPERATION, idempotencyKey));
            if (parcel == null || parcel.id() == null || parcel.trackingNumber() == null
                    || parcel.status() == null || parcel.createdAt() == null) {
                throw new IllegalStateException("Parcel service returned an incomplete response");
            }

            UUID parcelId = UUID.fromString(parcel.id());
            MerchantOrderStatus status = MerchantOrderStatus.valueOf(parcel.status());
            BigDecimal fee = parcel.fee() != null ? parcel.fee() : quote;
            MerchantOrderEntity entity = new MerchantOrderEntity(UUID.randomUUID(), merchantId, parcelId,
                    parcel.trackingNumber(), request.externalReference(), status, fee, parcel.createdAt());
            MerchantOrderEntity saved = orderRepository.saveAndFlush(entity);
            MerchantOrderResponse response = MerchantOrderResponse.from(saved);
            idempotencyStore.complete(merchantId, CREATE_OPERATION, idempotencyKey, 201, response);
            return new MerchantOrderIdempotencyStore.StoredResponse(201, response);
        } catch (RestClientException | IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Pricing or parcel service could not create the merchant order", exception);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order conflicts with an existing merchant order", exception);
        }
    }

    @Transactional
    public MerchantOrderIdempotencyStore.StoredResponse cancel(UUID merchantId, UUID orderId,
                                                               String idempotencyKey,
                                                               CancelMerchantOrderRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Merchant credentials are invalid, inactive, or not approved for ordering"));
        if (!merchant.getStatus().canPlaceOrders()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Merchant credentials are invalid, inactive, or not approved for ordering");
        }

        String hash = requestHasher.hash(CANCEL_OPERATION, request);
        MerchantOrderIdempotencyStore.StoredResponse replay =
                idempotencyStore.reserveOrReplay(merchantId, CANCEL_OPERATION, idempotencyKey, hash);
        if (replay != null) {
            return replay;
        }

        MerchantOrderEntity order = orderRepository.findByOrderIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant order was not found"));
        if (order.getStatus() != MerchantOrderStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Merchant orders can only be cancelled before pickup");
        }

        try {
            ParcelClient.InternalParcelResponse parcel = parcelClient.cancelParcel(order.getParcelId(),
                    request == null ? null : request.reason(),
                    requestHasher.downstreamKey(merchantId, CANCEL_OPERATION, idempotencyKey));
            if (parcel == null || parcel.id() == null || parcel.status() == null) {
                throw new IllegalStateException("Parcel service returned an incomplete cancellation response");
            }
            UUID parcelId = UUID.fromString(parcel.id());
            if (!order.getParcelId().equals(parcelId) || !"CANCELLED".equals(parcel.status())) {
                throw new IllegalStateException("Parcel service returned an unexpected cancellation response");
            }

            Instant updatedAt = parcel.updatedAt() != null ? parcel.updatedAt() : Instant.now();
            order.updateStatus(MerchantOrderStatus.CANCELLED, updatedAt);
            orderRepository.saveAndFlush(order);
            MerchantOrderResponse response = MerchantOrderResponse.from(order);
            idempotencyStore.complete(merchantId, CANCEL_OPERATION, idempotencyKey, 200, response);
            return new MerchantOrderIdempotencyStore.StoredResponse(200, response);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel was not found", exception);
            }
            if (exception.getStatusCode().value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Parcel is no longer eligible for cancellation", exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Parcel service could not cancel the merchant order", exception);
        } catch (RestClientException | IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Parcel service could not cancel the merchant order", exception);
        }
    }
}
