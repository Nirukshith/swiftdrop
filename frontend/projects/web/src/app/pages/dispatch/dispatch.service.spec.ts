import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  AssignmentResult,
  CourierWorkloadItem,
  DispatchParcelSummary,
  UnassignedParcelsPage,
} from './dispatch.model';
import { DispatchService } from './dispatch.service';

describe('DispatchService (Real API Integration)', () => {
  let service: DispatchService;
  let httpMock: HttpTestingController;

  const mockParcels: DispatchParcelSummary[] = [
    {
      parcelId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      trackingNumber: 'SD-20261025-A1B2C3',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-SOUTH',
      pickupAddress: '45 Galle Road, Colombo 03',
      dropoffAddress: '12 Havelock Road, Colombo 05',
      weightKg: 2.5,
      attemptCount: 0,
      failureReason: null,
      createdAt: '2026-10-25T10:00:00Z',
    },
    {
      parcelId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      trackingNumber: 'SD-20261025-B2C3D4',
      status: 'CREATED',
      pickupZone: 'ZONE-COLOMBO-NORTH',
      dropoffZone: 'ZONE-COLOMBO-NORTH',
      pickupAddress: '100 Union Place, Colombo 02',
      dropoffAddress: '88 Kandy Road, Kiribathgoda',
      weightKg: 1.2,
      attemptCount: 0,
      failureReason: null,
      createdAt: '2026-10-25T11:00:00Z',
    },
  ];

  const mockParcelsPage: UnassignedParcelsPage = {
    content: mockParcels,
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  };

  const mockCouriers: CourierWorkloadItem[] = [
    {
      courierId: 'f8e7d6c5-b4a3-2109-8765-43210fedcba9',
      name: 'Saman Perera',
      phoneNumber: '+94771234567',
      zone: 'ZONE-COLOMBO-NORTH',
      activeCount: 2,
      available: true,
      vehicleType: 'MOTORCYCLE',
    },
    {
      courierId: 'a2b3c4d5-e6f7-4819-9012-34567890abcd',
      name: 'Kamal Silva',
      phoneNumber: '+94772345678',
      zone: 'ZONE-COLOMBO-SOUTH',
      activeCount: 1,
      available: false,
      vehicleType: 'VAN',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DispatchService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(DispatchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created with initial empty signals and no hardcoded demo parcels', () => {
    expect(service).toBeTruthy();
    expect(service.parcels()).toEqual([]);
    expect(service.couriers()).toEqual([]);
    expect(service.unassignedCount()).toBe(0);
    expect(service.totalWorkloadActive()).toBe(0);
  });

  describe('loadUnassignedParcels (GET /api/v1/dispatch/parcels)', () => {
    it('should query unassigned parcels with default pagination parameters', () => {
      let result: UnassignedParcelsPage | undefined;
      service.loadUnassignedParcels().subscribe((res) => (result = res));

      expect(service.isLoadingParcels()).toBe(true);

      const req = httpMock.expectOne('/api/v1/dispatch/parcels?page=0&size=20');
      expect(req.request.method).toBe('GET');

      req.flush(mockParcelsPage);

      expect(service.isLoadingParcels()).toBe(false);
      expect(service.parcelsError()).toBeNull();
      expect(result).toEqual(mockParcelsPage);
      expect(service.parcels().length).toBe(2);
      expect(service.unassignedCount()).toBe(2);
      expect(service.currentPage()).toBe(0);
      expect(service.totalPages()).toBe(1);
    });

    it('should append zone and date query parameters when provided', () => {
      service.loadUnassignedParcels('ZONE-COLOMBO-NORTH', '2026-10-25', 1, 10).subscribe();

      const req = httpMock.expectOne(
        '/api/v1/dispatch/parcels?page=1&size=10&zone=ZONE-COLOMBO-NORTH&date=2026-10-25'
      );
      expect(req.request.method).toBe('GET');
      req.flush({
        content: [mockParcels[0]],
        totalElements: 1,
        totalPages: 1,
        page: 1,
        size: 10,
      });

      expect(service.parcels().length).toBe(1);
      expect(service.unassignedCount()).toBe(1);
    });

    it('should record parcelsError on HTTP failure (500 or network error)', () => {
      service.loadUnassignedParcels().subscribe({
        next: () => fail('Should have failed'),
        error: (err) => {
          expect(err.status).toBe(500);
        },
      });

      const req = httpMock.expectOne('/api/v1/dispatch/parcels?page=0&size=20');
      req.flush(
        { message: 'Database connection failed' },
        { status: 500, statusText: 'Internal Server Error' }
      );

      expect(service.isLoadingParcels()).toBe(false);
      expect(service.parcelsError()).toBe('Database connection failed');
      expect(service.parcels()).toEqual([]);
    });

    it('should record unauthorized error on 401 response', () => {
      service.loadUnassignedParcels().subscribe({
        next: () => fail('Should have failed'),
        error: (err) => expect(err.status).toBe(401),
      });

      const req = httpMock.expectOne('/api/v1/dispatch/parcels?page=0&size=20');
      req.flush({ message: 'Full authentication is required' }, { status: 401, statusText: 'Unauthorized' });

      expect(service.parcelsError()).toBe('Full authentication is required');
    });
  });

  describe('loadCourierWorkload (GET /api/v1/dispatch/couriers)', () => {
    it('should query couriers workload board and compute active metrics', () => {
      let couriers: CourierWorkloadItem[] | undefined;
      service.loadCourierWorkload().subscribe((res) => (couriers = res));

      expect(service.isLoadingCouriers()).toBe(true);

      const req = httpMock.expectOne('/api/v1/dispatch/couriers');
      expect(req.request.method).toBe('GET');
      req.flush(mockCouriers);

      expect(service.isLoadingCouriers()).toBe(false);
      expect(service.couriersError()).toBeNull();
      expect(couriers).toEqual(mockCouriers);
      expect(service.couriers().length).toBe(2);
      expect(service.availableCouriers().length).toBe(1);
      expect(service.availableCouriers()[0].name).toBe('Saman Perera');
      expect(service.totalWorkloadActive()).toBe(3); // 2 + 1
    });

    it('should pass zone and availableOnly query parameters', () => {
      service.loadCourierWorkload('ZONE-COLOMBO-NORTH', true).subscribe();

      const req = httpMock.expectOne(
        '/api/v1/dispatch/couriers?zone=ZONE-COLOMBO-NORTH&availableOnly=true'
      );
      expect(req.request.method).toBe('GET');
      req.flush([mockCouriers[0]]);

      expect(service.couriers().length).toBe(1);
    });

    it('should record couriersError on HTTP failure', () => {
      service.loadCourierWorkload().subscribe({
        next: () => fail('Should have failed'),
        error: (err) => expect(err.status).toBe(503),
      });

      const req = httpMock.expectOne('/api/v1/dispatch/couriers');
      req.flush({ message: 'Service Unavailable' }, { status: 503, statusText: 'Service Unavailable' });

      expect(service.isLoadingCouriers()).toBe(false);
      expect(service.couriersError()).toBe('Service Unavailable');
    });
  });

  describe('assignParcel (POST /api/v1/dispatch/assignments)', () => {
    const parcelId = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';
    const courierId = 'f8e7d6c5-b4a3-2109-8765-43210fedcba9';

    it('should submit real assignment request and refresh queue and workload on success', () => {
      const mockResult: AssignmentResult = {
        parcelId,
        courierId,
        assignedAt: '2026-10-25T12:00:00Z',
        status: 'ASSIGNED',
      };

      let result: AssignmentResult | undefined;
      service.assignParcel(parcelId, courierId).subscribe((res) => (result = res));

      const assignReq = httpMock.expectOne('/api/v1/dispatch/assignments');
      expect(assignReq.request.method).toBe('POST');
      expect(assignReq.request.body).toEqual({ parcelId, courierId });

      assignReq.flush(mockResult);

      expect(result).toEqual(mockResult);

      // assignParcel pipe triggers refreshQueue and refreshWorkload
      const queueRefreshReq = httpMock.expectOne('/api/v1/dispatch/parcels?page=0&size=20');
      queueRefreshReq.flush({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });

      const workloadRefreshReq = httpMock.expectOne('/api/v1/dispatch/couriers');
      workloadRefreshReq.flush([
        { ...mockCouriers[0], activeCount: 3 },
      ]);

      expect(service.unassignedCount()).toBe(0);
      expect(service.totalWorkloadActive()).toBe(3);
    });

    it('should propagate 409 Conflict when parcel is already assigned', () => {
      service.assignParcel(parcelId, courierId).subscribe({
        next: () => fail('Should have failed with 409'),
        error: (err) => {
          expect(err.status).toBe(409);
          expect(err.error.message).toBe('Parcel has already been assigned to another courier.');
        },
      });

      const req = httpMock.expectOne('/api/v1/dispatch/assignments');
      req.flush(
        {
          code: 'PARCEL_ALREADY_ASSIGNED',
          message: 'Parcel has already been assigned to another courier.',
          correlationId: 'c1-uuid',
          timestamp: '2026-10-25T12:00:00Z',
        },
        { status: 409, statusText: 'Conflict' }
      );
    });

    it('should propagate 400 Bad Request when courier is unavailable', () => {
      service.assignParcel(parcelId, courierId).subscribe({
        next: () => fail('Should have failed with 400'),
        error: (err) => {
          expect(err.status).toBe(400);
          expect(err.error.message).toBe('Courier is currently unavailable for assignments.');
        },
      });

      const req = httpMock.expectOne('/api/v1/dispatch/assignments');
      req.flush(
        {
          code: 'COURIER_UNAVAILABLE',
          message: 'Courier is currently unavailable for assignments.',
          correlationId: 'c2-uuid',
          timestamp: '2026-10-25T12:00:00Z',
        },
        { status: 400, statusText: 'Bad Request' }
      );
    });
  });

  describe('updateCourierAvailability (PATCH /api/v1/dispatch/couriers/{id}/availability)', () => {
    it('should send PATCH request and update local courier item in couriersSignal', () => {
      // First populate couriers
      service.loadCourierWorkload().subscribe();
      const loadReq = httpMock.expectOne('/api/v1/dispatch/couriers');
      loadReq.flush(mockCouriers);

      const courierId = mockCouriers[0].courierId;
      const updatedItem: CourierWorkloadItem = {
        ...mockCouriers[0],
        available: false,
      };

      service.updateCourierAvailability(courierId, false).subscribe();

      const patchReq = httpMock.expectOne(
        `/api/v1/dispatch/couriers/${courierId}/availability`
      );
      expect(patchReq.request.method).toBe('PATCH');
      expect(patchReq.request.body).toEqual({ available: false });

      patchReq.flush(updatedItem);

      const found = service.couriers().find((c) => c.courierId === courierId);
      expect(found?.available).toBe(false);
    });
  });

  describe('Staff User Management (Governance)', () => {
    it('should maintain staff users and allow provisioning accounts', () => {
      const initialCount = service.users().length;
      const created = service.createStaffUser({
        email: 'new.courier@swiftdrop.lk',
        password: 'Password123!',
        fullName: 'New Courier',
        phoneNumber: '+94771112222',
        role: 'COURIER',
      });

      expect(created.email).toBe('new.courier@swiftdrop.lk');
      expect(service.users().length).toBe(initialCount + 1);
    });

    it('should reject invalid role or duplicate email', () => {
      expect(() => {
        service.createStaffUser({
          email: 'test@swiftdrop.lk',
          password: 'Password123!',
          fullName: 'Test',
          phoneNumber: '+94771112222',
          role: 'ADMIN' as any,
        });
      }).toThrow(/Only COURIER or DISPATCHER/);

      expect(() => {
        service.createStaffUser({
          email: service.users()[0].email,
          password: 'Password123!',
          fullName: 'Duplicate',
          phoneNumber: '+94771112222',
          role: 'COURIER',
        });
      }).toThrow(/already exists/);
    });

    it('should toggle user status', () => {
      const user = service.users()[0];
      const initial = user.enabled;
      service.toggleUserStatus(user.id);
      expect(service.users().find((u) => u.id === user.id)?.enabled).toBe(!initial);
    });
  });
});
