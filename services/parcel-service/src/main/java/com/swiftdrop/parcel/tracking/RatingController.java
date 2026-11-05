package com.swiftdrop.parcel.tracking;

import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Delivery rating of a parcel (CU-07, parcel-read.yaml rateParcel / getParcelRating). */
@RestController
class RatingController {

    private final RatingService ratingService;

    RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/api/v1/parcels/{id}/rating")
    @ResponseStatus(HttpStatus.CREATED)
    RatingResponse rateParcel(@PathVariable String id, @Valid @RequestBody CreateRatingRequest request) {
        return ratingService.rate(id, request, CurrentUser.require());
    }

    @GetMapping("/api/v1/parcels/{id}/rating")
    RatingResponse getParcelRating(@PathVariable String id) {
        return ratingService.get(id, CurrentUser.require());
    }
}
