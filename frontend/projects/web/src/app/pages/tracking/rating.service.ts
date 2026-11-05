import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateRatingRequest, RatingResponse } from './tracking.model';

/**
 * Delivery rating of a parcel (parcel-read.yaml rateParcel / getParcelRating). The bearer token
 * is added by the shared-auth interceptor; the server checks owner, status and duplicates.
 */
@Injectable({ providedIn: 'root' })
export class RatingService {
  private readonly http = inject(HttpClient);

  static url(parcelId: string): string {
    return `/api/v1/parcels/${encodeURIComponent(parcelId)}/rating`;
  }

  /** The stored rating; 404 when the parcel has not been rated yet. */
  get(parcelId: string): Observable<RatingResponse> {
    return this.http.get<RatingResponse>(RatingService.url(parcelId));
  }

  /** 201 with the stored rating; 409 when not delivered or already rated. */
  rate(parcelId: string, request: CreateRatingRequest): Observable<RatingResponse> {
    return this.http.post<RatingResponse>(RatingService.url(parcelId), request);
  }
}
