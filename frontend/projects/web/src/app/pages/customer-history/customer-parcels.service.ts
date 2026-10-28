import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ParcelListQuery, ParcelPage } from './customer-history.model';

/**
 * Reads the signed-in customer's parcels from M1's GET /api/v1/parcels (newest first).
 * The bearer token is added by the shared-auth interceptor. In `ng serve --configuration mock`
 * the MSW mock layer answers instead.
 */
@Injectable({ providedIn: 'root' })
export class CustomerParcelsService {
  private readonly http = inject(HttpClient);
  static readonly URL = '/api/v1/parcels';

  list(query: ParcelListQuery = {}): Observable<ParcelPage> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    if (query.status) {
      params = params.set('status', query.status);
    }
    return this.http.get<ParcelPage>(CustomerParcelsService.URL, { params });
  }
}
