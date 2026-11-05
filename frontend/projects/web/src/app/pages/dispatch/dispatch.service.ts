import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import {
  AssignParcelRequest,
  AssignmentResult,
  CourierAvailabilityUpdateRequest,
  CourierWorkloadItem,
  DispatchParcelSummary,
  UnassignedParcelsPage,
} from './dispatch.model';

@Injectable({
  providedIn: 'root',
})
export class DispatchService {
  private readonly http = inject(HttpClient);
  public static readonly BASE_URL = '/api/v1/dispatch';

  // -------------------------------------------------------------------------
  // Signals State
  // -------------------------------------------------------------------------
  private parcelsSignal = signal<DispatchParcelSummary[]>([]);
  private couriersSignal = signal<CourierWorkloadItem[]>([]);
  private totalParcelsCount = signal<number>(0);
  private currentPageSignal = signal<number>(0);
  private totalPagesSignal = signal<number>(0);
  private pageSizeSignal = signal<number>(20);

  public isLoadingParcels = signal<boolean>(false);
  public isLoadingCouriers = signal<boolean>(false);
  public parcelsError = signal<string | null>(null);
  public couriersError = signal<string | null>(null);

  // Stored active filters for refresh
  private lastParcelsZone?: string;
  private lastParcelsDate?: string;
  private lastCouriersZone?: string;
  private lastCouriersAvailableOnly?: boolean;

  // -------------------------------------------------------------------------
  // Public Readonly Signals & Computeds
  // -------------------------------------------------------------------------
  public parcels = this.parcelsSignal.asReadonly();
  public unassignedParcels = this.parcelsSignal.asReadonly();
  public couriers = this.couriersSignal.asReadonly();

  public totalElements = this.totalParcelsCount.asReadonly();
  public currentPage = this.currentPageSignal.asReadonly();
  public totalPages = this.totalPagesSignal.asReadonly();
  public pageSize = this.pageSizeSignal.asReadonly();

  public unassignedCount = computed(() => this.totalParcelsCount());

  public availableCouriers = computed(() =>
    this.couriersSignal().filter((c) => c.available)
  );

  public availableCouriersCount = computed(
    () => this.availableCouriers().length
  );

  public totalWorkloadActive = computed(() =>
    this.couriersSignal().reduce((acc, c) => acc + (c.activeCount || 0), 0)
  );

  // -------------------------------------------------------------------------
  // API Methods
  // -------------------------------------------------------------------------

  /**
   * Load unassigned parcels queue from GET /api/v1/dispatch/parcels
   */
  public loadUnassignedParcels(
    zone?: string,
    date?: string,
    page = 0,
    size = 20
  ): Observable<UnassignedParcelsPage> {
    this.lastParcelsZone = zone;
    this.lastParcelsDate = date;
    this.isLoadingParcels.set(true);
    this.parcelsError.set(null);

    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (zone && zone.trim().length > 0) {
      params = params.set('zone', zone.trim());
    }
    if (date && date.trim().length > 0) {
      params = params.set('date', date.trim());
    }

    return this.http
      .get<UnassignedParcelsPage>(`${DispatchService.BASE_URL}/parcels`, {
        params,
      })
      .pipe(
        tap((pageData) => {
          this.parcelsSignal.set(pageData?.content || []);
          this.totalParcelsCount.set(pageData?.totalElements ?? pageData?.content?.length ?? 0);
          this.currentPageSignal.set(pageData?.page ?? page);
          this.totalPagesSignal.set(pageData?.totalPages ?? 1);
          this.pageSizeSignal.set(pageData?.size ?? size);
        }),
        catchError((err) => {
          const msg =
            err?.error?.message ||
            err?.message ||
            'Failed to load dispatch queue from server.';
          this.parcelsError.set(msg);
          return throwError(() => err);
        }),
        finalize(() => {
          this.isLoadingParcels.set(false);
        })
      );
  }

  /**
   * Load courier workload board from GET /api/v1/dispatch/couriers
   */
  public loadCourierWorkload(
    zone?: string,
    availableOnly = false
  ): Observable<CourierWorkloadItem[]> {
    this.lastCouriersZone = zone;
    this.lastCouriersAvailableOnly = availableOnly;
    this.isLoadingCouriers.set(true);
    this.couriersError.set(null);

    let params = new HttpParams();
    if (zone && zone.trim().length > 0) {
      params = params.set('zone', zone.trim());
    }
    if (availableOnly) {
      params = params.set('availableOnly', 'true');
    }

    return this.http
      .get<CourierWorkloadItem[]>(`${DispatchService.BASE_URL}/couriers`, {
        params,
      })
      .pipe(
        tap((items) => {
          this.couriersSignal.set(items || []);
        }),
        catchError((err) => {
          const msg =
            err?.error?.message ||
            err?.message ||
            'Failed to load courier workload from server.';
          this.couriersError.set(msg);
          return throwError(() => err);
        }),
        finalize(() => {
          this.isLoadingCouriers.set(false);
        })
      );
  }

  /**
   * Assign parcel to courier via POST /api/v1/dispatch/assignments
   */
  public assignParcel(
    parcelId: string,
    courierId: string
  ): Observable<AssignmentResult> {
    const payload: AssignParcelRequest = { parcelId, courierId };

    return this.http
      .post<AssignmentResult>(
        `${DispatchService.BASE_URL}/assignments`,
        payload
      )
      .pipe(
        tap(() => {
          // Immediately refresh unassigned queue and workload board to reflect persisted assignment
          this.refreshQueue();
          this.refreshWorkload();
        })
      );
  }

  /**
   * Toggle courier availability via PATCH /api/v1/dispatch/couriers/{id}/availability
   */
  public updateCourierAvailability(
    courierId: string,
    available: boolean
  ): Observable<CourierWorkloadItem> {
    const payload: CourierAvailabilityUpdateRequest = { available };

    return this.http
      .patch<CourierWorkloadItem>(
        `${DispatchService.BASE_URL}/couriers/${encodeURIComponent(courierId)}/availability`,
        payload
      )
      .pipe(
        tap((updated) => {
          this.couriersSignal.update((couriers) =>
            couriers.map((c) =>
              c.courierId === courierId ? { ...c, available: updated.available } : c
            )
          );
        })
      );
  }

  /**
   * Helper to re-fetch queue with last known filters and pagination
   */
  public refreshQueue(): void {
    this.loadUnassignedParcels(
      this.lastParcelsZone,
      this.lastParcelsDate,
      this.currentPageSignal(),
      this.pageSizeSignal()
    ).subscribe({
      error: () => {}, // Handled by loadUnassignedParcels error signal
    });
  }

  /**
   * Helper to re-fetch workload board with last known filters
   */
  public refreshWorkload(): void {
    this.loadCourierWorkload(
      this.lastCouriersZone,
      this.lastCouriersAvailableOnly
    ).subscribe({
      error: () => {}, // Handled by loadCourierWorkload error signal
    });
  }
}
