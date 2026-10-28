import { TestBed } from '@angular/core/testing';
import { DispatchService } from './dispatch.service';

describe('DispatchService', () => {
  let service: DispatchService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DispatchService],
    });
    service = TestBed.inject(DispatchService);
  });

  it('should be created and have initial state', () => {
    expect(service).toBeTruthy();
    expect(service.unassignedCount()).toBeGreaterThan(0);
    expect(service.couriers().length).toBeGreaterThan(0);
    expect(service.users().length).toBeGreaterThan(0);
  });

  it('assignParcel should mark parcel as ASSIGNED and increment courier activeCount', () => {
    const parcel = service.unassignedParcels()[0];
    const availableCourier = service.availableCouriers()[0];
    const initialActiveCount = availableCourier.activeCount;

    service.assignParcel(parcel.parcelId, availableCourier.courierId);

    const updatedParcel = service
      .parcels()
      .find((p) => p.parcelId === parcel.parcelId);
    expect(updatedParcel?.status).toBe('ASSIGNED');
    expect(updatedParcel?.assignedCourierId).toBe(availableCourier.courierId);

    const updatedCourier = service
      .couriers()
      .find((c) => c.courierId === availableCourier.courierId);
    expect(updatedCourier?.activeCount).toBe(initialActiveCount + 1);
  });

  it('assignParcel should throw error when courier is offline', () => {
    const parcel = service.unassignedParcels()[0];
    const offlineCourier = service
      .couriers()
      .find((c) => !c.available);

    expect(offlineCourier).toBeDefined();
    if (offlineCourier) {
      expect(() => {
        service.assignParcel(parcel.parcelId, offlineCourier.courierId);
      }).toThrow(/unavailable/);
    }
  });

  it('assignParcel should throw error when parcel is already assigned', () => {
    const parcel = service.unassignedParcels()[0];
    const availableCourier = service.availableCouriers()[0];

    service.assignParcel(parcel.parcelId, availableCourier.courierId);

    expect(() => {
      service.assignParcel(parcel.parcelId, availableCourier.courierId);
    }).toThrow(/already assigned/);
  });

  it('updateCourierAvailability should toggle availability', () => {
    const courier = service.couriers()[0];
    const current = courier.available;

    service.updateCourierAvailability(courier.courierId, !current);

    const updated = service
      .couriers()
      .find((c) => c.courierId === courier.courierId);
    expect(updated?.available).toBe(!current);
  });

  it('createStaffUser should create a courier account and add to couriers workload', () => {
    const initialUserCount = service.users().length;
    const initialCourierCount = service.couriers().length;

    const created = service.createStaffUser({
      email: 'new.courier@swiftdrop.lk',
      password: 'SecurePassword123',
      fullName: 'New Courier',
      phoneNumber: '+94771112222',
      role: 'COURIER',
    });

    expect(created.email).toBe('new.courier@swiftdrop.lk');
    expect(created.role).toBe('COURIER');
    expect(service.users().length).toBe(initialUserCount + 1);
    expect(service.couriers().length).toBe(initialCourierCount + 1);
  });

  it('createStaffUser should reject duplicate email', () => {
    const existingEmail = service.users()[0].email;

    expect(() => {
      service.createStaffUser({
        email: existingEmail,
        password: 'Password123',
        fullName: 'Duplicate User',
        phoneNumber: '+94770000000',
        role: 'COURIER',
      });
    }).toThrow(/already exists/);
  });

  it('toggleUserStatus should flip user enabled state', () => {
    const user = service.users()[0];
    const initialStatus = user.enabled;

    service.toggleUserStatus(user.id);

    const updated = service.users().find((u) => u.id === user.id);
    expect(updated?.enabled).toBe(!initialStatus);
  });
});
