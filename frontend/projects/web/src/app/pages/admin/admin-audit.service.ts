import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap, catchError, throwError } from 'rxjs';

export interface AuditRecord {
  id: string;
  occurredAt: string;
  actorId: string;
  actorRole: string;
  action: string;
  targetType: string;
  targetId: string;
  ipAddress?: string;
  correlationId?: string;
  details?: Record<string, any>;
}

export interface AuditLogPage {
  content: AuditRecord[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

@Injectable({
  providedIn: 'root',
})
export class AdminAuditService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/admin/audit';

  public readonly activities = signal<AuditRecord[]>([]);
  public readonly totalElements = signal<number>(0);
  public readonly totalPages = signal<number>(0);
  public readonly currentPage = signal<number>(0);
  public readonly pageSize = signal<number>(10);
  public readonly loading = signal<boolean>(false);
  public readonly error = signal<string | null>(null);

  loadAuditLogs(page: number = 0, size: number = 10): Observable<AuditLogPage> {
    this.loading.set(true);
    this.error.set(null);

    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('administrativeOnly', 'true');

    return this.http.get<AuditLogPage>(this.baseUrl, { params }).pipe(
      tap((res) => {
        this.activities.set(res.content || []);
        this.totalElements.set(res.totalElements ?? 0);
        this.totalPages.set(res.totalPages ?? 0);
        this.currentPage.set(res.page ?? page);
        this.pageSize.set(res.size ?? size);
        this.loading.set(false);
      }),
      catchError((err) => {
        const msg = err?.error?.message || err?.message || 'Failed to load administrative activities.';
        this.error.set(msg);
        this.loading.set(false);
        return throwError(() => err);
      })
    );
  }

  loadSecurityAlerts(limit: number = 5): Observable<AuditLogPage> {
    const params = new HttpParams()
      .set('action', 'ACCOUNT_LOCKED')
      .set('page', '0')
      .set('size', limit.toString());

    return this.http.get<AuditLogPage>(this.baseUrl, { params });
  }
}

