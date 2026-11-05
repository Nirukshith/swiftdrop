import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, tap, throwError } from 'rxjs';
import {
  AssignParcelRequest,
  AssignmentResult,
  CourierAvailabilityUpdateRequest,
  CourierWorkloadItem,
  CreateStaffPayload,
  DispatchParcelSummary,
  StaffUser,
  UnassignedParcelsPage,
  PricingRule,
  PricingRuleRequest,
  Zone,
  ZoneRequest,
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
  public zonesError = signal<string | null>(null);
  public pricingRulesError = signal<string | null>(null);
  public isLoadingZones = signal(false);
  public isLoadingPricingRules = signal(false);

  // Stored active filters for refresh
  private lastParcelsZone?: string;
  private lastParcelsDate?: string;
  private lastCouriersZone?: string;
  private lastCouriersAvailableOnly?: boolean;

  // Staff users state for admin user management
  private usersSignal = signal<StaffUser[]>([
    {
      id: 'usr-admin-1',
      email: 'admin.lead@swiftdrop.lk',
      fullName: 'Agnes Ostina',
      phoneNumber: '+94 77 000 1111',
      role: 'ADMIN',
      enabled: true,
      createdAt: '2026-10-20T08:00:00Z',
    },
    {
      id: 'usr-dispatch-1',
      email: 'dispatcher.colombo@swiftdrop.lk',
      fullName: 'Chathura Wickramasinghe',
      phoneNumber: '+94 77 222 4444',
      role: 'DISPATCHER',
      enabled: true,
      createdAt: '2026-10-22T09:30:00Z',
    },
    {
      id: 'f8e7d6c5-b4a3-2109-8765-43210fedcba9',
      email: 'courier.saman@swiftdrop.lk',
      fullName: 'Saman Perera',
      phoneNumber: '+94 77 123 4567',
      role: 'COURIER',
      enabled: true,
      createdAt: '2026-10-23T10:15:00Z',
    },
  ]);

  // -------------------------------------------------------------------------
  // Public Readonly Signals & Computeds
  // -------------------------------------------------------------------------
  public parcels = this.parcelsSignal.asReadonly();
  public unassignedParcels = this.parcelsSignal.asReadonly();
  public couriers = this.couriersSignal.asReadonly();
  public users = this.usersSignal.asReadonly();

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

  public listZones(): Observable<Zone[]> {
    this.isLoadingZones.set(true);
    this.zonesError.set(null);
    return this.http.get<Zone[]>('/api/v1/admin/zones').pipe(
      catchError((err) => {
        this.zonesError.set(err?.error?.message || err?.message || 'Failed to load zones.');
        return throwError(() => err);
      }),
      finalize(() => this.isLoadingZones.set(false))
    );
  }

  public createZone(request: ZoneRequest): Observable<Zone> {
    return this.http.post<Zone>('/api/v1/admin/zones', request);
  }

  public updateZone(id: string, request: ZoneRequest): Observable<Zone> {
    return this.http.put<Zone>(`/api/v1/admin/zones/${encodeURIComponent(id)}`, request);
  }

  public deleteZone(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/zones/${encodeURIComponent(id)}`);
  }

  public listPricingRules(): Observable<PricingRule[]> {
    this.isLoadingPricingRules.set(true);
    this.pricingRulesError.set(null);
    return this.http.get<PricingRule[]>('/api/v1/admin/pricing-rules').pipe(
      catchError((err) => {
        this.pricingRulesError.set(err?.error?.message || err?.message || 'Failed to load pricing rules.');
        return throwError(() => err);
      }),
      finalize(() => this.isLoadingPricingRules.set(false))
    );
  }

  public createPricingRule(request: PricingRuleRequest): Observable<PricingRule> {
    return this.http.post<PricingRule>('/api/v1/admin/pricing-rules', request);
  }

  public updatePricingRule(id: string, request: PricingRuleRequest): Observable<PricingRule> {
    return this.http.put<PricingRule>(
      `/api/v1/admin/pricing-rules/${encodeURIComponent(id)}`,
      request
    );
  }

  public deletePricingRule(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/admin/pricing-rules/${encodeURIComponent(id)}`);
  }

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

  // -------------------------------------------------------------------------
  // Staff User Management Methods (Admin Users Governance)
  // -------------------------------------------------------------------------
  public createStaffUser(payload: CreateStaffPayload): StaffUser {
    if (payload.role !== 'COURIER' && payload.role !== 'DISPATCHER') {
      throw new Error('Only COURIER or DISPATCHER accounts can be provisioned.');
    }

    const existing = this.usersSignal().find(
      (u) => u.email.toLowerCase() === payload.email.trim().toLowerCase()
    );
    if (existing) {
      throw new Error(`User account with email ${payload.email} already exists.`);
    }

    const newId = `usr-${Date.now().toString(36)}`;
    const newUser: StaffUser = {
      id: newId,
      email: payload.email.trim().toLowerCase(),
      fullName: payload.fullName.trim(),
      phoneNumber: payload.phoneNumber.trim(),
      role: payload.role,
      enabled: true,
      createdAt: new Date().toISOString(),
    };

    this.usersSignal.update((users) => [newUser, ...users]);
    return newUser;
  }

  public toggleUserStatus(userId: string): void {
    this.usersSignal.update((users) =>
      users.map((u) => {
        if (u.id === userId) {
          return { ...u, enabled: !u.enabled };
        }
        return u;
      })
    );
  }
}
