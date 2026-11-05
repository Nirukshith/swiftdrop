import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourierService } from './courier.service';
import {
  CourierDeliveriesResponse,
  CourierDeliveryDetail,
  CourierStatusUpdateResponse,
  ProofUploadResponse,
} from './courier.model';

describe('CourierService', () => {
  let service: CourierService;
  let httpMock: HttpTestingController;

  const mockDeliveriesResponse: CourierDeliveriesResponse = {
    date: '2026-10-28',
    totalDeliveries: 2,
    pendingCount: 2,
    completedCount: 0,
    deliveries: [
      {
        parcelId: 'pcl-55001',
        trackingNumber: 'SD-20261028-001',
        status: 'OUT_FOR_DELIVERY',
        recipientName: 'Sunil Weerasinghe',
        recipientPhone: '+94 77 123 4567',
        pickupAddress: '100 Union Place, Colombo 02',
        dropoffAddress: '75 Galle Road, Colombo 04',
        googleMapsUrl: 'https://maps.google.com/?q=Colombo',
        weightKg: 1.5,
        attemptCount: 1,
        sequenceOrder: 1,
      },
      {
        parcelId: 'pcl-55002',
        trackingNumber: 'SD-20261028-002',
        status: 'CREATED',
        recipientName: 'Kamal Perera',
        recipientPhone: '+94 71 987 6543',
        pickupAddress: '54 Bauddhaloka Mw, Colombo 07',
        dropoffAddress: '12 Havelock Place, Colombo 05',
        googleMapsUrl: 'https://maps.google.com/?q=Havelock',
        weightKg: 2.0,
        attemptCount: 0,
        sequenceOrder: 2,
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), CourierService],
    });

    service = TestBed.inject(CourierService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created with initial state', () => {
    expect(service).toBeTruthy();
    expect(service.deliveries().length).toBe(0);
    expect(service.isLoading()).toBe(false);
  });

  it('should fetch today deliveries via GET /api/v1/courier/deliveries', () => {
    service.getDeliveries('2026-10-28').subscribe((res) => {
      expect(res.totalDeliveries).toBe(2);
      expect(service.deliveries().length).toBe(2);
      expect(service.deliveries()[0].trackingNumber).toBe('SD-20261028-001');
    });

    const req = httpMock.expectOne((r) => r.url === '/api/v1/courier/deliveries' && r.params.get('date') === '2026-10-28');
    expect(req.request.method).toBe('GET');
    req.flush(mockDeliveriesResponse);
  });

  it('should pass status filter query param when provided', () => {
    service.getDeliveries('2026-10-28', 'OUT_FOR_DELIVERY').subscribe();

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/courier/deliveries' &&
        r.params.get('date') === '2026-10-28' &&
        r.params.get('status') === 'OUT_FOR_DELIVERY'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockDeliveriesResponse);
  });

  it('should track loading state via loadDeliveries', () => {
    service.loadDeliveries().subscribe();
    expect(service.isLoading()).toBe(true);

    const req = httpMock.expectOne('/api/v1/courier/deliveries');
    req.flush(mockDeliveriesResponse);

    expect(service.isLoading()).toBe(false);
    expect(service.deliveries().length).toBe(2);
  });

  it('should fetch single delivery detail via GET /api/v1/courier/deliveries/{parcelId}', () => {
    const mockDetail: CourierDeliveryDetail = {
      parcelId: 'pcl-55001',
      trackingNumber: 'SD-20261028-001',
      status: 'OUT_FOR_DELIVERY',
      recipientName: 'Sunil Weerasinghe',
      recipientPhone: '+94 77 123 4567',
      senderName: 'Daraz Hub',
      senderPhone: '+94 11 234 5678',
      pickupAddress: '100 Union Place, Colombo 02',
      dropoffAddress: '75 Galle Road, Colombo 04',
      googleMapsUrl: 'https://maps.google.com',
      specialInstructions: 'Ring bell twice',
      weightKg: 1.5,
      attemptCount: 1,
      allowedNextStatuses: ['DELIVERED', 'FAILED'],
      proofRequired: true,
      proof: null,
      failureReason: null,
    };

    service.getDeliveryDetail('pcl-55001').subscribe((detail) => {
      expect(detail.parcelId).toBe('pcl-55001');
      expect(detail.specialInstructions).toBe('Ring bell twice');
      expect(service.getDeliveryById('pcl-55001')?.specialInstructions).toBe('Ring bell twice');
    });

    const req = httpMock.expectOne('/api/v1/courier/deliveries/pcl-55001');
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);
  });

  it('should update status via PATCH /api/v1/courier/deliveries/{parcelId}/status', () => {
    // Populate service with initial deliveries
    service.getDeliveries().subscribe();
    httpMock.expectOne('/api/v1/courier/deliveries').flush(mockDeliveriesResponse);

    const statusResponse: CourierStatusUpdateResponse = {
      parcelId: 'pcl-55001',
      trackingNumber: 'SD-20261028-001',
      previousStatus: 'OUT_FOR_DELIVERY',
      currentStatus: 'DELIVERED',
      updatedAt: '2026-10-28T14:00:00Z',
      allowedNextStatuses: [],
    };

    service
      .updateStatus('pcl-55001', {
        status: 'DELIVERED',
        recipientName: 'Sunil Weerasinghe',
        idempotencyKey: 'idem-1',
      })
      .subscribe((res) => {
        expect(res.currentStatus).toBe('DELIVERED');
        expect(service.getDeliveryById('pcl-55001')?.status).toBe('DELIVERED');
      });

    const req = httpMock.expectOne('/api/v1/courier/deliveries/pcl-55001/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      status: 'DELIVERED',
      recipientName: 'Sunil Weerasinghe',
      idempotencyKey: 'idem-1',
    });
    req.flush(statusResponse);
  });

  it('should upload delivery proof via POST /api/v1/courier/deliveries/{parcelId}/proof', () => {
    service.getDeliveries().subscribe();
    httpMock.expectOne('/api/v1/courier/deliveries').flush(mockDeliveriesResponse);

    const file = new File(['dummy-bytes'], 'proof.jpg', { type: 'image/jpeg' });
    const proofResponse: ProofUploadResponse = {
      proofId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
      parcelId: 'pcl-55001',
      proofUrl: '/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6',
      mimeType: 'image/jpeg',
      fileSizeBytes: 11,
      uploadedAt: '2026-10-28T14:30:00Z',
    };

    service
      .uploadProof('pcl-55001', {
        file,
        recipientName: 'Sunil Weerasinghe',
        notes: 'Signed at door',
      })
      .subscribe((res) => {
        expect(res.proofId).toBe(proofResponse.proofId);
        expect(service.getDeliveryById('pcl-55001')?.proof?.proofUrl).toBe(proofResponse.proofUrl);
      });

    const req = httpMock.expectOne('/api/v1/courier/deliveries/pcl-55001/proof');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(proofResponse);
  });

  it('should reject proof upload client-side if file exceeds 5MB', (done) => {
    const largeFile = {
      size: 6 * 1024 * 1024,
      name: 'too-big.jpg',
      type: 'image/jpeg',
    } as File;

    service
      .uploadProof('pcl-55001', {
        file: largeFile,
        recipientName: 'Sunil',
      })
      .subscribe({
        error: (err) => {
          expect(err.message).toContain('exceeds 5MB');
          done();
        },
      });
  });

  it('should calculate stats accurately based on loaded deliveries', () => {
    service.getDeliveries().subscribe();
    httpMock.expectOne('/api/v1/courier/deliveries').flush(mockDeliveriesResponse);

    const stats = service.stats();
    expect(stats.total).toBe(2);
    expect(stats.outForDelivery).toBe(1);
    expect(stats.created).toBe(1);
    expect(stats.delivered).toBe(0);
  });

  it('should generate a contract-shaped daily summary for M9 compatibility', () => {
    service.getDeliveries().subscribe();
    httpMock.expectOne('/api/v1/courier/deliveries').flush(mockDeliveriesResponse);

    const summary = service.getDailySummary('2026-10-28');
    expect(summary.date).toBe('2026-10-28');
    expect(summary.totalDeliveries).toBe(2);
    expect(summary.pendingCount).toBe(2);
    expect(summary.completedCount).toBe(0);
    expect(summary.deliveries.length).toBe(2);
  });

  it('should toggle courier availability for M9 compatibility', () => {
    const initial = service.profile().availability;
    service.toggleAvailability();
    expect(service.profile().availability).toBe(!initial);
  });
});
