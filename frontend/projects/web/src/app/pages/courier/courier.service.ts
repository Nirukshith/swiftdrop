import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, tap, throwError } from 'rxjs';
import {
  CourierDailySummary,
  CourierDeliveriesResponse,
  CourierDelivery,
  CourierDeliveryCard,
  CourierDeliveryDetail,
  CourierProfile,
  CourierStats,
  CourierStatusUpdateRequest,
  CourierStatusUpdateResponse,
  DeliveryProof,
  DeliveryStatus,
  ProofUploadPayload,
  ProofUploadResponse,
} from './courier.model';

@Injectable({
  providedIn: 'root',
})
export class CourierService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/courier/deliveries';

  private readonly defaultCourierId = 'courier-harsha-01';

  // -------------------------------------------------------------------------
  // Signals State
  // -------------------------------------------------------------------------
  private readonly deliveriesSignal = signal<CourierDelivery[]>([]);
  private readonly isLoadingSignal = signal<boolean>(false);
  private readonly errorSignal = signal<string | null>(null);

  private readonly profileSignal = signal<CourierProfile>({
    userId: this.defaultCourierId,
    name: 'Harsha Kumarasamy',
    phone: '+94 77 123 4567',
    zone: 'Colombo Central Zone',
    availability: true,
    vehicle: 'MOTORBIKE',
  });

  // Public readonly signals
  readonly deliveries = this.deliveriesSignal.asReadonly();
  readonly isLoading = this.isLoadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly profile = this.profileSignal.asReadonly();

  // -------------------------------------------------------------------------
  // Computed Statistics
  // -------------------------------------------------------------------------
  readonly stats = computed<CourierStats>(() => {
    const list = this.deliveriesSignal();
    const isCreated = (s: string) => s === 'CREATED' || s === 'ASSIGNED';
    const isFailed = (s: string) => s === 'FAILED' || s === 'FAILED_DELIVERY';

    return {
      total: list.length,
      created: list.filter((d) => isCreated(d.status)).length,
      assigned: list.filter((d) => isCreated(d.status)).length,
      pickedUp: list.filter((d) => d.status === 'PICKED_UP').length,
      inTransit: list.filter((d) => d.status === 'IN_TRANSIT').length,
      outForDelivery: list.filter((d) => d.status === 'OUT_FOR_DELIVERY').length,
      delivered: list.filter((d) => d.status === 'DELIVERED').length,
      failed: list.filter((d) => isFailed(d.status)).length,
    };
  });

  // -------------------------------------------------------------------------
  // Real API Calls
  // -------------------------------------------------------------------------

  /**
   * Fetch today's deliveries from GET /api/v1/courier/deliveries
   */
  getDeliveries(date?: string, status?: string): Observable<CourierDeliveriesResponse> {
    let params = new HttpParams();
    if (date && date.trim()) {
      params = params.set('date', date.trim());
    }
    if (status && status.trim()) {
      params = params.set('status', status.trim());
    }

    return this.http.get<CourierDeliveriesResponse>(this.baseUrl, { params }).pipe(
      tap((res) => {
        const mapped = (res.deliveries || []).map((card) => this.mapCardToDelivery(card));
        this.deliveriesSignal.set(mapped);
      })
    );
  }

  /**
   * Convenience loader with loading/error signal tracking
   */
  loadDeliveries(date?: string, status?: string): Observable<CourierDeliveriesResponse> {
    this.isLoadingSignal.set(true);
    this.errorSignal.set(null);

    return this.getDeliveries(date, status).pipe(
      catchError((err) => {
        const errorMsg = err?.error?.message || err?.message || 'Failed to load deliveries';
        this.errorSignal.set(errorMsg);
        return throwError(() => err);
      }),
      finalize(() => {
        this.isLoadingSignal.set(false);
      })
    );
  }

  /**
   * Fetch single delivery detail from GET /api/v1/courier/deliveries/{parcelId}
   */
  getDeliveryDetail(parcelId: string): Observable<CourierDeliveryDetail> {
    return this.http.get<CourierDeliveryDetail>(`${this.baseUrl}/${parcelId}`).pipe(
      tap((detail) => {
        this.mergeDetail(detail);
      })
    );
  }

  /**
   * Look up delivery by parcelId in memory
   */
  getDeliveryById(parcelId: string): CourierDelivery | undefined {
    return this.deliveriesSignal().find((d) => d.parcelId === parcelId);
  }

  /**
   * Update status via PATCH /api/v1/courier/deliveries/{parcelId}/status
   */
  updateStatus(parcelId: string, req: CourierStatusUpdateRequest): Observable<CourierStatusUpdateResponse> {
    return this.http.patch<CourierStatusUpdateResponse>(`${this.baseUrl}/${parcelId}/status`, req).pipe(
      tap((res) => {
        this.deliveriesSignal.update((list) =>
          list.map((d) => {
            if (d.parcelId === parcelId) {
              return {
                ...d,
                status: res.currentStatus as DeliveryStatus,
                updatedAt: res.updatedAt || new Date().toISOString(),
                allowedNextStatuses: res.allowedNextStatuses,
                failureReason: res.currentStatus === 'FAILED' ? req.reason || d.failureReason : d.failureReason,
              };
            }
            return d;
          })
        );
      })
    );
  }

  /**
   * Upload proof photo via POST /api/v1/courier/deliveries/{parcelId}/proof
   */
  uploadProof(parcelId: string, payload: ProofUploadPayload): Observable<ProofUploadResponse> {
    if (payload.file && payload.file.size > 5 * 1024 * 1024) {
      return throwError(() => new Error('File exceeds 5MB size limit'));
    }

    const formData = new FormData();
    formData.append('file', payload.file, payload.file.name);
    if (payload.recipientName) {
      formData.append('recipientName', payload.recipientName);
    }
    const notes = payload.notes || payload.deliveryNotes;
    if (notes) {
      formData.append('notes', notes);
    }

    return this.http.post<ProofUploadResponse>(`${this.baseUrl}/${parcelId}/proof`, formData).pipe(
      tap((res) => {
        this.deliveriesSignal.update((list) =>
          list.map((d) => {
            if (d.parcelId === parcelId) {
              const proofInfo: DeliveryProof = {
                id: res.proofId,
                parcelId: res.parcelId,
                courierId: d.courierId || this.defaultCourierId,
                fileName: payload.file.name,
                fileSize: res.fileSizeBytes,
                contentType: res.mimeType,
                recipientName: payload.recipientName || d.recipientName,
                deliveryNotes: notes || null,
                uploadedAt: res.uploadedAt,
                proofUrl: res.proofUrl,
              };
              return {
                ...d,
                proof: proofInfo,
              };
            }
            return d;
          })
        );
      })
    );
  }

  // -------------------------------------------------------------------------
  // M9 Compatibility
  // -------------------------------------------------------------------------

  getDailySummary(date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(new Date())): CourierDailySummary {
    const list = this.deliveriesSignal();
    const deliveries: CourierDeliveryCard[] = list.map((d, index) => ({
      parcelId: d.parcelId,
      trackingNumber: d.trackingNumber,
      status: d.status,
      recipientName: d.recipientName,
      recipientPhone: d.recipientPhone,
      dropoffAddress: d.dropoffAddress || (d.recipientAddress ? `${d.recipientAddress.line1}, ${d.recipientAddress.city}` : ''),
      googleMapsUrl: d.googleMapsUrl || null,
      weightKg: d.weightKg,
      attemptCount: d.attemptCount ?? 1,
      sequenceOrder: d.sequenceOrder ?? index + 1,
      completedAt: d.status === 'DELIVERED' ? d.updatedAt || null : null,
    }));

    const pendingStatuses = new Set(['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY']);
    const completedStatuses = new Set(['DELIVERED', 'FAILED', 'FAILED_DELIVERY']);

    return {
      date,
      totalDeliveries: deliveries.length,
      pendingCount: deliveries.filter((d) => pendingStatuses.has(d.status)).length,
      completedCount: deliveries.filter((d) => completedStatuses.has(d.status)).length,
      deliveries,
    };
  }

  toggleAvailability(): CourierProfile {
    this.profileSignal.update((profile) => ({ ...profile, availability: !profile.availability }));
    return this.profileSignal();
  }

  // -------------------------------------------------------------------------
  // Helper Mappers
  // -------------------------------------------------------------------------

  private mapCardToDelivery(card: CourierDeliveryCard): CourierDelivery {
    return {
      parcelId: card.parcelId,
      trackingNumber: card.trackingNumber,
      status: card.status as DeliveryStatus,
      recipientName: card.recipientName,
      recipientPhone: card.recipientPhone || '',
      pickupAddress: card.pickupAddress || '',
      dropoffAddress: card.dropoffAddress,
      recipientAddress: {
        line1: card.dropoffAddress,
        city: '',
        district: '',
      },
      senderName: 'SwiftDrop Merchant',
      senderAddress: {
        line1: card.pickupAddress || '',
        city: '',
        district: '',
      },
      googleMapsUrl: card.googleMapsUrl,
      weightKg: card.weightKg,
      attemptCount: card.attemptCount,
      sequenceOrder: card.sequenceOrder,
      updatedAt: card.completedAt || new Date().toISOString(),
    };
  }

  private mergeDetail(detail: CourierDeliveryDetail): void {
    const updated: CourierDelivery = {
      parcelId: detail.parcelId,
      trackingNumber: detail.trackingNumber,
      status: detail.status as DeliveryStatus,
      recipientName: detail.recipientName,
      recipientPhone: detail.recipientPhone,
      senderName: detail.senderName || 'SwiftDrop Merchant',
      senderPhone: detail.senderPhone,
      pickupAddress: detail.pickupAddress,
      dropoffAddress: detail.dropoffAddress,
      recipientAddress: {
        line1: detail.dropoffAddress,
        city: '',
        district: '',
      },
      senderAddress: {
        line1: detail.pickupAddress,
        city: '',
        district: '',
      },
      specialInstructions: detail.specialInstructions,
      weightKg: detail.weightKg,
      attemptCount: detail.attemptCount,
      allowedNextStatuses: detail.allowedNextStatuses,
      proofRequired: detail.proofRequired,
      proof: detail.proof,
      failureReason: detail.failureReason,
      googleMapsUrl: detail.googleMapsUrl,
      updatedAt: new Date().toISOString(),
    };

    this.deliveriesSignal.update((list) => {
      const idx = list.findIndex((d) => d.parcelId === detail.parcelId);
      if (idx >= 0) {
        const copy = [...list];
        copy[idx] = { ...copy[idx], ...updated };
        return copy;
      }
      return [updated, ...list];
    });
  }
}
