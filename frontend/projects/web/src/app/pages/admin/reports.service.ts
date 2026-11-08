import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, finalize, Observable, tap, throwError } from 'rxjs';
import { DailyReportRow } from './reports.model';

@Injectable({
  providedIn: 'root',
})
export class ReportsService {
  private readonly http = inject(HttpClient);
  private readonly rowsSignal = signal<DailyReportRow[]>([]);

  readonly rows = this.rowsSignal.asReadonly();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  load(from: string, to: string): Observable<DailyReportRow[]> {
    this.loading.set(true);
    this.error.set(null);
    const params = new HttpParams().set('from', from).set('to', to);

    return this.http.get<DailyReportRow[]>('/api/v1/admin/reports/daily', { params }).pipe(
      tap((rows) => this.rowsSignal.set(rows)),
      catchError((err) => {
        this.error.set(err?.error?.message || err?.message || 'Failed to load delivery reports.');
        return throwError(() => err);
      }),
      finalize(() => this.loading.set(false))
    );
  }
}
