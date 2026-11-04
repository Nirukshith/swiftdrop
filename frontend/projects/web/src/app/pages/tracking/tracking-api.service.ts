import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ParcelDetail, ParcelHistoryResponse, PublicTrackingResponse } from './tracking.model';

/** REST reads for the tracking pages. The bearer token is added by the shared-auth interceptor. */
@Injectable({ providedIn: 'root' })
export class TrackingApiService {
  private readonly http = inject(HttpClient);

  /** Public masked lookup (no login). 404 for unknown and malformed numbers. */
  publicTracking(trackingNumber: string): Observable<PublicTrackingResponse> {
    return this.http.get<PublicTrackingResponse>(`/api/v1/tracking/${encodeURIComponent(trackingNumber)}`);
  }

  /** M1's parcel detail: owner customer, assigned courier, dispatcher or admin. */
  parcel(id: string): Observable<ParcelDetail> {
    return this.http.get<ParcelDetail>(`/api/v1/parcels/${encodeURIComponent(id)}`);
  }

  /** Status history of a parcel (same access rule as the detail). */
  history(id: string): Observable<ParcelHistoryResponse> {
    return this.http.get<ParcelHistoryResponse>(`/api/v1/parcels/${encodeURIComponent(id)}/history`);
  }
}
