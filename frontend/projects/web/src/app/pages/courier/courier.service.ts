import { Injectable, computed, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import {
  CourierDelivery,
  CourierStats,
  DeliveryProof,
  DeliveryStatus,
  ProofUploadPayload,
  StatusUpdateRequest,
  CourierDailySummary,
  CourierProfile,
} from './courier.model';

@Injectable({
  providedIn: 'root',
})
export class CourierService {
  private readonly defaultCourierId = 'courier-harsha-01';

  // State
  private deliveriesSignal = signal<CourierDelivery[]>([
    {
      parcelId: 'pcl-55001',
      trackingNumber: 'SD-20261028-001',
      courierId: this.defaultCourierId,
      status: 'IN_TRANSIT',
      recipientName: 'Sunil Weerasinghe',
      recipientPhone: '+94 77 123 4567',
      recipientAddress: {
        line1: '75 Galle Road',
        line2: 'Bambalapitiya',
        city: 'Colombo 04',
        district: 'Colombo',
        postalCode: '00400',
      },
      senderName: 'Daraz Hub Colombo',
      senderAddress: {
        line1: '100 Union Place',
        city: 'Colombo 02',
        district: 'Colombo',
        postalCode: '00200',
      },
      specialInstructions: 'Call before arriving. Ring doorbell twice.',
      weightKg: 1.5,
      sizeCategory: 'SMALL',
      timeSlot: 'Morning (09:00 - 12:00)',
      proof: null,
      failureReason: null,
      updatedAt: new Date(Date.now() - 1800000).toISOString(),
      createdAt: new Date(Date.now() - 7200000).toISOString(),
    },
    {
      parcelId: 'pcl-55002',
      trackingNumber: 'SD-20261028-002',
      courierId: this.defaultCourierId,
      status: 'PICKED_UP',
      recipientName: 'Dr. Anoma Jayawardena',
      recipientPhone: '+94 71 987 6543',
      recipientAddress: {
        line1: '12 Havelock Place',
        line2: 'Apt 3A',
        city: 'Colombo 05',
        district: 'Colombo',
        postalCode: '00500',
      },
      senderName: 'Apex Health Supplies',
      senderAddress: {
        line1: '54 Bauddhaloka Mawatha',
        city: 'Colombo 07',
        district: 'Colombo',
        postalCode: '00700',
      },
      specialInstructions: 'Fragile medical sample. Keep upright.',
      weightKg: 0.6,
      sizeCategory: 'SMALL',
      timeSlot: 'Afternoon (13:00 - 16:00)',
      proof: null,
      failureReason: null,
      updatedAt: new Date(Date.now() - 3600000).toISOString(),
      createdAt: new Date(Date.now() - 10800000).toISOString(),
    },
    {
      parcelId: 'pcl-55003',
      trackingNumber: 'SD-20261028-003',
      courierId: this.defaultCourierId,
      status: 'ASSIGNED',
      recipientName: 'Kamal Bandara',
      recipientPhone: '+94 76 555 1122',
      recipientAddress: {
        line1: '240 Kandy Road',
        line2: 'Near Clock Tower',
        city: 'Kadawatha',
        district: 'Gampaha',
        postalCode: '11850',
      },
      senderName: 'Softlogic Electronics',
      senderAddress: {
        line1: 'Level 2, Majestic City',
        city: 'Colombo 04',
        district: 'Colombo',
        postalCode: '00400',
      },
      specialInstructions: 'Verify recipient ID upon handover.',
      weightKg: 3.2,
      sizeCategory: 'MEDIUM',
      timeSlot: 'Evening (16:00 - 19:00)',
      proof: null,
      failureReason: null,
      updatedAt: new Date(Date.now() - 5400000).toISOString(),
      createdAt: new Date(Date.now() - 14400000).toISOString(),
    },
    {
      parcelId: 'pcl-55004',
      trackingNumber: 'SD-20261028-004',
      courierId: this.defaultCourierId,
      status: 'DELIVERED',
      recipientName: 'Nadeeka Fernando',
      recipientPhone: '+94 72 333 4455',
      recipientAddress: {
        line1: '18/4 Ward Place',
        line2: null,
        city: 'Colombo 07',
        district: 'Colombo',
        postalCode: '00700',
      },
      senderName: 'Odel Department Store',
      senderAddress: {
        line1: '5 Alexandra Place',
        city: 'Colombo 07',
        district: 'Colombo',
        postalCode: '00700',
      },
      specialInstructions: 'Leave with security guard if unavailable.',
      weightKg: 1.1,
      sizeCategory: 'SMALL',
      timeSlot: 'Morning (09:00 - 12:00)',
      proof: {
        id: 'prf-9901',
        parcelId: 'pcl-55004',
        courierId: this.defaultCourierId,
        fileName: 'delivery_proof_ward_place.jpg',
        fileSize: 420100,
        contentType: 'image/jpeg',
        recipientName: 'Nadeeka Fernando',
        deliveryNotes: 'Delivered at front door and signed by recipient',
        uploadedAt: new Date(Date.now() - 7200000).toISOString(),
      },
      failureReason: null,
      updatedAt: new Date(Date.now() - 7200000).toISOString(),
      createdAt: new Date(Date.now() - 18000000).toISOString(),
    },
    {
      parcelId: 'pcl-55005',
      trackingNumber: 'SD-20261028-005',
      courierId: this.defaultCourierId,
      status: 'FAILED_DELIVERY',
      recipientName: 'Roshan Silva',
      recipientPhone: '+94 75 888 9900',
      recipientAddress: {
        line1: '49 Main Street',
        city: 'Moratuwa',
        district: 'Colombo',
        postalCode: '10400',
      },
      senderName: 'Singer Mega Store',
      senderAddress: {
        line1: '320 Union Place',
        city: 'Colombo 02',
        district: 'Colombo',
        postalCode: '00200',
      },
      specialInstructions: 'Building closed on Poya days.',
      weightKg: 5.4,
      sizeCategory: 'LARGE',
      timeSlot: 'Morning (09:00 - 12:00)',
      proof: null,
      failureReason: 'Premises closed, recipient unreachable via phone after 3 attempts.',
      updatedAt: new Date(Date.now() - 10800000).toISOString(),
      createdAt: new Date(Date.now() - 21600000).toISOString(),
    },
  ]);

  private readonly profileSignal = signal<CourierProfile>({
    userId: this.defaultCourierId,
    name: 'Harsha Kumarasamy',
    phone: '+94 77 123 4567',
    zone: 'Colombo Central Zone',
    availability: true,
    vehicle: 'MOTORBIKE',
  });

  readonly profile = this.profileSignal.asReadonly();

  // Readonly signal for public view
  readonly deliveries = this.deliveriesSignal.asReadonly();

  // Computed statistics
  readonly stats = computed<CourierStats>(() => {
    const list = this.deliveriesSignal();
    return {
      total: list.length,
      assigned: list.filter((d) => d.status === 'ASSIGNED').length,
      pickedUp: list.filter((d) => d.status === 'PICKED_UP').length,
      inTransit: list.filter((d) => d.status === 'IN_TRANSIT').length,
      delivered: list.filter((d) => d.status === 'DELIVERED').length,
      failed: list.filter((d) => d.status === 'FAILED_DELIVERY').length,
    };
  });

  getDeliveries(): Observable<CourierDelivery[]> {
    return of(this.deliveriesSignal());
  }

  getDeliveryById(parcelId: string): CourierDelivery | undefined {
    return this.deliveriesSignal().find((d) => d.parcelId === parcelId);
  }

  getDailySummary(date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo' }).format(new Date())): CourierDailySummary {
    const deliveries = this.deliveriesSignal().map((delivery, index) => ({
      parcelId: delivery.parcelId,
      trackingNumber: delivery.trackingNumber,
      status: delivery.status,
      recipientName: delivery.recipientName,
      recipientPhone: delivery.recipientPhone,
      dropoffAddress: [delivery.recipientAddress.line1, delivery.recipientAddress.city].join(', '),
      googleMapsUrl: null,
      weightKg: delivery.weightKg,
      attemptCount: 1,
      sequenceOrder: index + 1,
      completedAt: delivery.status === 'DELIVERED' ? delivery.updatedAt : null,
    }));
    const pendingStatuses = new Set(['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT']);
    const completedStatuses = new Set(['DELIVERED', 'FAILED_DELIVERY']);
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

  updateStatus(parcelId: string, req: StatusUpdateRequest): Observable<CourierDelivery> {
    const current = this.getDeliveryById(parcelId);
    if (!current) {
      throw new Error(`Delivery with parcelId ${parcelId} not found`);
    }

    // Validate state machine
    this.validateTransition(current.status, req.status);

    const updated: CourierDelivery = {
      ...current,
      status: req.status,
      failureReason: req.status === 'FAILED_DELIVERY' ? req.reason || 'Failed delivery' : current.failureReason,
      updatedAt: new Date().toISOString(),
    };

    // Update signal
    this.deliveriesSignal.update((list) =>
      list.map((d) => (d.parcelId === parcelId ? updated : d))
    );

    return of(updated);
  }

  uploadProof(parcelId: string, payload: ProofUploadPayload): Observable<CourierDelivery> {
    const current = this.getDeliveryById(parcelId);
    if (!current) {
      throw new Error(`Delivery with parcelId ${parcelId} not found`);
    }

    if (payload.file.size > 5 * 1024 * 1024) {
      throw new Error('File exceeds 5MB size limit');
    }

    const proof: DeliveryProof = {
      id: `prf-${Date.now()}`,
      parcelId,
      courierId: current.courierId,
      fileName: payload.file.name,
      fileSize: payload.file.size,
      contentType: payload.file.type || 'image/jpeg',
      recipientName: payload.recipientName,
      deliveryNotes: payload.deliveryNotes || null,
      uploadedAt: new Date().toISOString(),
    };

    const updated: CourierDelivery = {
      ...current,
      status: 'DELIVERED',
      proof,
      failureReason: null,
      updatedAt: new Date().toISOString(),
    };

    this.deliveriesSignal.update((list) =>
      list.map((d) => (d.parcelId === parcelId ? updated : d))
    );

    return of(updated);
  }

  private validateTransition(current: DeliveryStatus, target: DeliveryStatus): void {
    if (current === target) {
      return; // Safe retry / idempotent
    }

    const validMoves: Record<DeliveryStatus, DeliveryStatus[]> = {
      ASSIGNED: ['PICKED_UP'],
      PICKED_UP: ['IN_TRANSIT'],
      IN_TRANSIT: ['DELIVERED', 'FAILED_DELIVERY'],
      DELIVERED: [],
      FAILED_DELIVERY: ['IN_TRANSIT'], // retry allowed
    };

    const allowed = validMoves[current] || [];
    if (!allowed.includes(target)) {
      throw new Error(`Cannot transition delivery from ${current} to ${target}`);
    }
  }
}
