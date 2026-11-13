import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap } from 'rxjs';
import {
  CreateCustomerParcelRequest,
  CustomerAddress,
  CustomerParcel,
  CustomerParcelPage,
  District,
  ParcelSize,
  ParcelStatus,
  QuoteResponse,
} from './customer.model';

type CustomerAddressRequest = Omit<CustomerAddress, 'id' | 'customerId' | 'createdAt'>;

@Injectable({
  providedIn: 'root',
})
export class CustomerService {
  private readonly http = inject(HttpClient);
  // =========================================================================
  // Address Book Operations
  // =========================================================================

  listAddresses(): Observable<CustomerAddress[]> {
    return this.http.get<CustomerAddress[]>('/api/v1/addresses');
  }

  createAddress(
    addressData: Omit<CustomerAddress, 'id' | 'customerId' | 'createdAt'>
  ): Observable<CustomerAddress> {
    return this.http.post<CustomerAddress>('/api/v1/addresses', addressData);
  }

  updateAddress(
    id: string,
    changes: CustomerAddressRequest
  ): Observable<CustomerAddress> {
    return this.http.put<CustomerAddress>(`/api/v1/addresses/${encodeURIComponent(id)}`, changes);
  }

  deleteAddress(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/addresses/${encodeURIComponent(id)}`);
  }

  setDefaultAddress(id: string): Observable<CustomerAddress> {
    return this.http.get<CustomerAddress[]>('/api/v1/addresses').pipe(
      map((addresses) => addresses.find((address) => address.id === id)),
      switchMap((address) => {
        if (!address) {
          throw new Error('Address not found');
        }
        return this.http.put<CustomerAddress>(
          `/api/v1/addresses/${encodeURIComponent(id)}`,
          {
            label: address.label,
            line1: address.line1,
            line2: address.line2,
            city: address.city,
            district: address.district,
            postalCode: address.postalCode,
            isDefault: true,
          }
        );
      })
    );
  }

  // =========================================================================
  // Fee Calculation & Quotes (Real Backend via parcel-service)
  // =========================================================================

  getFeeQuote(
    pickupDistrict: District,
    dropoffDistrict: District,
    weightKg: number,
    size: ParcelSize
  ): Observable<QuoteResponse> {
    const params = new HttpParams()
      .set('pickupDistrict', pickupDistrict)
      .set('dropoffDistrict', dropoffDistrict)
      .set('weightKg', weightKg.toString())
      .set('size', size);

    return this.http.get<QuoteResponse>('/api/v1/parcels/quote', { params });
  }

  // =========================================================================
  // Parcel Operations (Real Backend via parcel-service)
  // =========================================================================

  listParcels(
    statusFilter?: ParcelStatus,
    page = 0,
    size = 20
  ): Observable<CustomerParcelPage> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (statusFilter) {
      params = params.set('status', statusFilter);
    }
    return this.http.get<CustomerParcelPage>('/api/v1/parcels', { params });
  }

  getParcel(id: string): Observable<CustomerParcel | undefined> {
    return this.http.get<CustomerParcel>(`/api/v1/parcels/${encodeURIComponent(id)}`);
  }

  createParcel(
    request: CreateCustomerParcelRequest
  ): Observable<CustomerParcel> {
    return this.http.post<CustomerParcel>('/api/v1/parcels', request);
  }

  cancelParcel(id: string, reason?: string): Observable<CustomerParcel> {
    return this.http.post<CustomerParcel>(
      `/api/v1/parcels/${encodeURIComponent(id)}/cancel`,
      { reason: reason || 'Cancelled by customer' }
    );
  }
}
