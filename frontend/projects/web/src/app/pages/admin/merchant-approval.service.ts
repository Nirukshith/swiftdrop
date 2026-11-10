import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, finalize, Observable, tap, throwError } from 'rxjs';
import {
  MerchantApprovalResponse,
  MerchantPage,
  MerchantRejectionRequest,
  MerchantStatus,
  MerchantSummary,
} from './merchant-approval.model';

@Injectable({
  providedIn: 'root',
})
export class MerchantApprovalService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/admin/merchants';

  private readonly merchantsSignal = signal<MerchantSummary[]>([]);
  private readonly totalElementsSignal = signal(0);
  private readonly totalPagesSignal = signal(0);
  private readonly currentPageSignal = signal(0);
  private readonly pageSizeSignal = signal(20);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly merchants = this.merchantsSignal.asReadonly();
  readonly totalElements = this.totalElementsSignal.asReadonly();
  readonly totalPages = this.totalPagesSignal.asReadonly();
  readonly currentPage = this.currentPageSignal.asReadonly();
  readonly pageSize = this.pageSizeSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  private lastPage = 0;
  private lastSize = 20;

  loadMerchants(page = 0, size = 20): Observable<MerchantPage> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    this.lastPage = page;
    this.lastSize = size;

    const params = new HttpParams()
      .set('status', 'PENDING')
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<MerchantPage>(this.baseUrl, { params }).pipe(
      tap((response) => {
        this.merchantsSignal.set(response.content ?? []);
        this.totalElementsSignal.set(response.totalElements ?? 0);
        this.totalPagesSignal.set(response.totalPages ?? 0);
        this.currentPageSignal.set(response.page ?? page);
        this.pageSizeSignal.set(response.size ?? size);
      }),
      catchError((error) => {
        this.errorSignal.set(this.messageFor(error, 'Pending merchants could not be loaded.'));
        return throwError(() => error);
      }),
      finalize(() => this.loadingSignal.set(false))
    );
  }

  refresh(): Observable<MerchantPage> {
    return this.loadMerchants(this.lastPage, this.lastSize);
  }

  approve(merchantId: string): Observable<MerchantApprovalResponse> {
    return this.http.post<MerchantApprovalResponse>(`${this.baseUrl}/${merchantId}/approve`, {});
  }

  reject(merchantId: string, request: MerchantRejectionRequest): Observable<MerchantSummary> {
    return this.http.post<MerchantSummary>(`${this.baseUrl}/${merchantId}/reject`, request);
  }

  clearError(): void {
    this.errorSignal.set(null);
  }

  messageFor(error: unknown, fallback: string): string {
    if (!error || typeof error !== 'object') return fallback;
    const response = error as { status?: number; error?: { message?: string }; message?: string };
    if (response.status === 409) {
      return 'This merchant is no longer pending. Refresh the list and try again.';
    }
    return response.error?.message || response.message || fallback;
  }
}
