import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, finalize, Observable, tap, throwError } from 'rxjs';
import { DailyReportRow, ZoneReportSummary } from './reports.model';
import { Zone } from '../dispatch/dispatch.model';

@Injectable({
  providedIn: 'root',
})
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly rowsSignal = signal<DailyReportRow[]>([]);
  private readonly zoneSummariesSignal = signal<ZoneReportSummary[]>([]);
  private readonly availableZonesSignal = signal<Zone[]>([]);

  readonly rows = this.rowsSignal.asReadonly();
  readonly zoneSummaries = this.zoneSummariesSignal.asReadonly();
  readonly availableZones = this.availableZonesSignal.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly zonesLoading = signal(false);
  readonly exporting = signal(false);
  readonly exportError = signal<string | null>(null);

  load(from: string, to: string, zone?: string): Observable<DailyReportRow[]> {
    this.loading.set(true);
    this.error.set(null);
    let params = new HttpParams().set('from', from).set('to', to);
    if (zone && zone.trim() !== '') {
      params = params.set('zone', zone.trim());
    }

    return this.http.get<DailyReportRow[]>('/api/v1/admin/reports/daily', { params }).pipe(
      tap((rows) => this.rowsSignal.set(rows)),
      catchError((err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load delivery reports.');
        return throwError(() => err);
      }),
      finalize(() => this.loading.set(false))
    );
  }

  loadZoneReports(from: string, to: string): Observable<ZoneReportSummary[]> {
    const params = new HttpParams().set('from', from).set('to', to);

    return this.http.get<ZoneReportSummary[]>('/api/v1/admin/reports/zones', { params }).pipe(
      tap((summaries) => this.zoneSummariesSignal.set(summaries)),
      catchError((err) => {
        return throwError(() => err);
      })
    );
  }

  loadAvailableZones(): Observable<Zone[]> {
    this.zonesLoading.set(true);
    return this.http.get<Zone[]>('/api/v1/admin/zones').pipe(
      tap((zones) => this.availableZonesSignal.set(zones)),
      catchError((err) => throwError(() => err)),
      finalize(() => this.zonesLoading.set(false))
    );
  }

  exportCsv(from: string, to: string, zone?: string): Observable<HttpResponse<Blob>> {
    this.exporting.set(true);
    this.exportError.set(null);
    let params = new HttpParams().set('from', from).set('to', to);
    if (zone && zone.trim() !== '') {
      params = params.set('zone', zone.trim());
    }

    return this.http.get('/api/v1/admin/reports/daily.csv', {
      params,
      observe: 'response',
      responseType: 'blob',
    }).pipe(
      catchError((err) => {
        this.exportError.set('Failed to export delivery reports.');
        return throwError(() => err);
      }),
      finalize(() => this.exporting.set(false))
    );
  }
}
