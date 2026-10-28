import { Injectable, computed, signal } from '@angular/core';
import {
  CourierWorkload,
  CreateStaffPayload,
  StaffUser,
  UnassignedParcel,
} from './dispatch.model';

@Injectable({
  providedIn: 'root',
})
export class DispatchService {
  // -------------------------------------------------------------------------
  // Signals State
  // -------------------------------------------------------------------------
  private parcelsSignal = signal<UnassignedParcel[]>([
    {
      parcelId: 'pcl-101',
      trackingNumber: 'SD-20261028-001',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-SOUTH',
      pickupAddress: '45 Galle Road, Colombo 03',
      dropoffAddress: '12 Havelock Road, Colombo 05',
      weightKg: 2.5,
      attemptCount: 0,
      failureReason: null,
      createdAt: new Date(Date.now() - 3600000).toISOString(),
      recipientName: 'Sunil Weerasinghe',
      recipientPhone: '+94 77 123 4567',
    },
    {
      parcelId: 'pcl-102',
      trackingNumber: 'SD-20261028-002',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-NORTH',
      pickupAddress: '100 Union Place, Colombo 02',
      dropoffAddress: '88 Kandy Road, Kiribathgoda',
      weightKg: 1.2,
      attemptCount: 0,
      failureReason: null,
      createdAt: new Date(Date.now() - 5400000).toISOString(),
      recipientName: 'Dr. Anoma Jayawardena',
      recipientPhone: '+94 71 987 6543',
    },
    {
      parcelId: 'pcl-103',
      trackingNumber: 'SD-20261028-003',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-SOUTH',
      dropoffZone: 'ZONE-KANDY',
      pickupAddress: '18 De Saram Place, Colombo 10',
      dropoffAddress: '55 Peradeniya Road, Kandy',
      weightKg: 4.8,
      attemptCount: 0,
      failureReason: null,
      createdAt: new Date(Date.now() - 7200000).toISOString(),
      recipientName: 'Kavinda Perera',
      recipientPhone: '+94 75 444 8888',
    },
    {
      parcelId: 'pcl-104',
      trackingNumber: 'SD-20261028-004',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-NORTH',
      pickupAddress: '15 High Level Road, Nugegoda',
      dropoffAddress: '23 Rajagiriya Road, Rajagiriya',
      weightKg: 0.8,
      attemptCount: 0,
      failureReason: null,
      createdAt: new Date(Date.now() - 1800000).toISOString(),
      recipientName: 'Malini Fonseka',
      recipientPhone: '+94 76 222 3333',
    },
  ]);

  private couriersSignal = signal<CourierWorkload[]>([
    {
      courierId: 'f8e7d6c5-b4a3-2109-8765-43210fedcba9',
      name: 'Saman Perera',
      phoneNumber: '+94 77 123 4567',
      zone: 'ZONE-COLOMBO-NORTH',
      activeCount: 2,
      available: true,
      vehicleType: 'MOTORCYCLE',
    },
    {
      courierId: 'a2b3c4d5-e6f7-4819-9012-34567890abcd',
      name: 'Kamal Silva',
      phoneNumber: '+94 77 234 5678',
      zone: 'ZONE-COLOMBO-SOUTH',
      activeCount: 1,
      available: true,
      vehicleType: 'VAN',
    },
    {
      courierId: 'b3c4d5e6-f7a8-4920-0123-45678901bcde',
      name: 'Nimal Fernando',
      phoneNumber: '+94 77 345 6789',
      zone: 'ZONE-COLOMBO-NORTH',
      activeCount: 0,
      available: false,
      vehicleType: 'MOTORCYCLE',
    },
    {
      courierId: 'c4d5e6f7-a8b9-4031-1234-56789012cdef',
      name: 'Dinesh Bandara',
      phoneNumber: '+94 71 888 9999',
      zone: 'ZONE-KANDY',
      activeCount: 4,
      available: true,
      vehicleType: 'THREE_WHEELER',
    },
  ]);

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
    {
      id: 'a2b3c4d5-e6f7-4819-9012-34567890abcd',
      email: 'courier.kamal@swiftdrop.lk',
      fullName: 'Kamal Silva',
      phoneNumber: '+94 77 234 5678',
      role: 'COURIER',
      enabled: true,
      createdAt: '2026-10-24T11:00:00Z',
    },
    {
      id: 'b3c4d5e6-f7a8-4920-0123-45678901bcde',
      email: 'courier.nimal@swiftdrop.lk',
      fullName: 'Nimal Fernando',
      phoneNumber: '+94 77 345 6789',
      role: 'COURIER',
      enabled: false,
      createdAt: '2026-10-24T14:20:00Z',
    },
  ]);

  // -------------------------------------------------------------------------
  // Public Readonly Signals & Computeds
  // -------------------------------------------------------------------------
  public parcels = this.parcelsSignal.asReadonly();
  public couriers = this.couriersSignal.asReadonly();
  public users = this.usersSignal.asReadonly();

  public unassignedParcels = computed(() =>
    this.parcelsSignal().filter((p) => p.status === 'CREATED')
  );

  public unassignedCount = computed(() => this.unassignedParcels().length);

  public availableCouriers = computed(() =>
    this.couriersSignal().filter((c) => c.available)
  );

  public availableCouriersCount = computed(
    () => this.availableCouriers().length
  );

  public totalWorkloadActive = computed(() =>
    this.couriersSignal().reduce((acc, c) => acc + c.activeCount, 0)
  );

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------

  /**
   * Assigns an unassigned parcel to an available courier.
   * Updates parcel status to 'ASSIGNED' and increments courier activeCount.
   */
  public assignParcel(parcelId: string, courierId: string): void {
    const parcel = this.parcelsSignal().find((p) => p.parcelId === parcelId);
    if (!parcel) {
      throw new Error(`Parcel not found: ${parcelId}`);
    }
    if (parcel.status !== 'CREATED') {
      throw new Error(
        `Parcel ${parcel.trackingNumber} is already assigned or in transit.`
      );
    }

    const courier = this.couriersSignal().find(
      (c) => c.courierId === courierId
    );
    if (!courier) {
      throw new Error(`Courier not found: ${courierId}`);
    }
    if (!courier.available) {
      throw new Error(
        `Courier ${courier.name} is currently offline and unavailable for assignment.`
      );
    }

    // 1. Update parcel state
    this.parcelsSignal.update((parcels) =>
      parcels.map((p) =>
        p.parcelId === parcelId
          ? { ...p, status: 'ASSIGNED', assignedCourierId: courierId }
          : p
      )
    );

    // 2. Increment courier workload count (simulating domain event consumption)
    this.couriersSignal.update((couriers) =>
      couriers.map((c) =>
        c.courierId === courierId
          ? { ...c, activeCount: c.activeCount + 1 }
          : c
      )
    );
  }

  /**
   * Toggles courier availability status.
   */
  public updateCourierAvailability(
    courierId: string,
    available: boolean
  ): void {
    this.couriersSignal.update((couriers) =>
      couriers.map((c) =>
        c.courierId === courierId ? { ...c, available } : c
      )
    );
  }

  /**
   * Provisions a new staff account (COURIER or DISPATCHER).
   */
  public createStaffUser(payload: CreateStaffPayload): StaffUser {
    if (payload.role !== 'COURIER' && payload.role !== 'DISPATCHER') {
      throw new Error('Only COURIER or DISPATCHER accounts can be provisioned.');
    }

    const existing = this.usersSignal().find(
      (u) => u.email.toLowerCase() === payload.email.trim().toLowerCase()
    );
    if (existing) {
      throw new Error(
        `User account with email ${payload.email} already exists.`
      );
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

    // If a courier was provisioned, register them on the workload board
    if (payload.role === 'COURIER') {
      const newCourier: CourierWorkload = {
        courierId: newId,
        name: payload.fullName.trim(),
        phoneNumber: payload.phoneNumber.trim(),
        zone: 'ZONE-COLOMBO-NORTH',
        activeCount: 0,
        available: true,
        vehicleType: 'MOTORCYCLE',
      };
      this.couriersSignal.update((couriers) => [...couriers, newCourier]);
    }

    return newUser;
  }

  /**
   * Toggles enabled/disabled status for a user.
   */
  public toggleUserStatus(userId: string): void {
    let targetRole: string | undefined;

    this.usersSignal.update((users) =>
      users.map((u) => {
        if (u.id === userId) {
          targetRole = u.role;
          return { ...u, enabled: !u.enabled };
        }
        return u;
      })
    );

    // If disabled courier, set offline
    if (targetRole === 'COURIER') {
      const user = this.usersSignal().find((u) => u.id === userId);
      if (user && !user.enabled) {
        this.updateCourierAvailability(userId, false);
      }
    }
  }
}
