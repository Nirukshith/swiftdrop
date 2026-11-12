import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface BulkRowResult {
  rowNumber: number;
  accepted: boolean;
  order?: { orderId: string; parcelId: string; trackingNumber: string; externalReference: string; status: string; fee: number } | null;
  errors: string[];
}
export interface BulkResult { totalRows: number; acceptedRows: number; rejectedRows: number; results: BulkRowResult[]; }

@Injectable({ providedIn: 'root' })
export class MerchantBulkService {
  private readonly http = inject(HttpClient);
  submit(csv: string, key: string) {
    return this.http.post<BulkResult>('/api/v1/merchant/orders/bulk', csv, {
      headers: new HttpHeaders({ 'Content-Type': 'text/csv', 'Idempotency-Key': key }),
    });
  }
}
