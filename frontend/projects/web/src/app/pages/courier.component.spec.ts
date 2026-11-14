import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourierComponent } from './courier.component';
import { CourierService } from './courier/courier.service';
import { CourierDeliveriesResponse } from './courier/courier.model';

describe('CourierComponent', () => {
  let component: CourierComponent;
  let fixture: ComponentFixture<CourierComponent>;
  let httpMock: HttpTestingController;

  const mockDeliveriesResponse: CourierDeliveriesResponse = {
    date: '2026-10-28',
    totalDeliveries: 3,
    pendingCount: 2,
    completedCount: 1,
    deliveries: [
      {
        parcelId: 'pcl-55001',
        trackingNumber: 'SD-20261028-001',
        status: 'OUT_FOR_DELIVERY',
        recipientName: 'Sunil Weerasinghe',
        recipientPhone: '+94 77 123 4567',
        pickupAddress: '100 Union Place, Colombo 02',
        dropoffAddress: '75 Galle Road, Colombo 04',
        googleMapsUrl: 'https://www.google.com/maps/search/?api=1&query=75+Galle+Road+Colombo+04',
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
        googleMapsUrl: 'https://www.google.com/maps/search/?api=1&query=12+Havelock+Place+Colombo+05',
        weightKg: 0.8,
        attemptCount: 0,
        sequenceOrder: 2,
      },
      {
        parcelId: 'pcl-55003',
        trackingNumber: 'SD-20261028-003',
        status: 'DELIVERED',
        recipientName: 'Nadeeka Fernando',
        recipientPhone: '+94 72 333 4455',
        pickupAddress: '5 Alexandra Place, Colombo 07',
        dropoffAddress: '18/4 Ward Place, Colombo 07',
        googleMapsUrl: 'https://www.google.com/maps/search/?api=1&query=18%2F4+Ward+Place+Colombo+07',
        weightKg: 2.1,
        attemptCount: 1,
        sequenceOrder: 3,
      },
    ],
  };

  const mockProfile = {
    userId: 'courier-1',
    name: 'Kamal Perera',
    phone: '+94771234567',
    zone: 'Colombo',
    availability: true,
    vehicle: 'MOTORBIKE',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CourierComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), CourierService],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CourierComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Flush initial ngOnInit deliveries load
    const req = httpMock.expectOne('/api/v1/courier/deliveries');
    expect(req.request.method).toBe('GET');
    req.flush(mockDeliveriesResponse);
    const profileReq = httpMock.expectOne('/api/v1/courier/me');
    expect(profileReq.request.method).toBe('GET');
    profileReq.flush(mockProfile);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the courier component', () => {
    expect(component).toBeTruthy();
  });

  it('should display deliveries and stats loaded from backend', () => {
    expect(component.filteredDeliveries().length).toBe(3);
    expect(component.stats().total).toBe(3);
    expect(component.stats().delivered).toBe(1);
    expect(component.stats().created).toBe(1);
    expect(component.stats().outForDelivery).toBe(1);
  });

  it('loads and displays the authenticated courier profile in the dashboard header', () => {
    expect(component.profileLoaded()).toBe(true);
    expect(component.profileLoading()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Kamal Perera');
    expect(fixture.nativeElement.textContent).toContain('courier-1');
    expect(fixture.nativeElement.textContent).toContain('Colombo');
  });

  it('shows the profile API error and does not display stale profile values', () => {
    component.loadProfile();
    const profileReq = httpMock.expectOne('/api/v1/courier/me');
    profileReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(component.profileLoaded()).toBe(false);
    expect(component.profileLoading()).toBe(false);
    expect(component.profileError()).toContain('session has expired');
    expect(fixture.nativeElement.textContent).toContain('session has expired');
    const headerText = fixture.nativeElement.querySelector('h1').parentElement.textContent;
    expect(headerText).not.toContain('Kamal Perera');
    expect(fixture.nativeElement.textContent).not.toContain('courier-1');
  });

  it('reports a missing courier profile without fabricating dashboard identity', () => {
    component.loadProfile();
    const profileReq = httpMock.expectOne('/api/v1/courier/me');
    profileReq.flush({ message: 'Courier profile not found' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(component.profileLoaded()).toBe(false);
    expect(component.profileError()).toBe('No courier profile is available for this account.');
    expect(fixture.nativeElement.textContent).toContain('No courier profile is available');
    const headerText = fixture.nativeElement.querySelector('h1').parentElement.textContent;
    expect(headerText).not.toContain('Kamal Perera');
    expect(fixture.nativeElement.textContent).not.toContain('courier-1');
  });

  it('should filter deliveries when changing filter tab', () => {
    // ACTIVE tab
    component.activeFilter.set('ACTIVE');
    const active = component.filteredDeliveries();
    expect(active.length).toBe(2);
    expect(
      active.every((d) =>
        ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(d.status)
      )
    ).toBe(true);

    // DELIVERED tab
    component.activeFilter.set('DELIVERED');
    const delivered = component.filteredDeliveries();
    expect(delivered.length).toBe(1);
    expect(delivered[0].status).toBe('DELIVERED');

    // FAILED tab
    component.activeFilter.set('FAILED');
    const failed = component.filteredDeliveries();
    expect(failed.length).toBe(0);
  });

  it('should filter deliveries by search query', () => {
    component.searchQuery.set('Sunil');
    const results = component.filteredDeliveries();
    expect(results.length).toBe(1);
    expect(results[0].recipientName).toContain('Sunil');
  });

  it('should advance delivery status on action click', fakeAsync(() => {
    const created = component.filteredDeliveries().find((d) => d.status === 'CREATED');
    expect(created).toBeTruthy();

    if (created) {
      component.advanceStatus(created, 'PICKED_UP');
      tick();

      const patchReq = httpMock.expectOne(`/api/v1/courier/deliveries/${created.parcelId}/status`);
      expect(patchReq.request.method).toBe('PATCH');
      expect(patchReq.request.body.status).toBe('PICKED_UP');

      patchReq.flush({
        parcelId: created.parcelId,
        trackingNumber: created.trackingNumber,
        previousStatus: 'CREATED',
        currentStatus: 'PICKED_UP',
        updatedAt: '2026-10-28T14:15:00Z',
        allowedNextStatuses: ['IN_TRANSIT'],
      });
      tick();

      const updated = component.filteredDeliveries().find((d) => d.parcelId === created.parcelId);
      expect(updated?.status).toBe('PICKED_UP');
    }
  }));

  it('should open and close proof modal with recipient prefilled', () => {
    const delivery = component.filteredDeliveries()[0];
    component.openProofModal(delivery);

    expect(component.selectedDeliveryForProof()).toBe(delivery);
    expect(component.proofRecipientName).toBe(delivery.recipientName);

    component.closeProofModal();
    expect(component.selectedDeliveryForProof()).toBeNull();
  });

  it('should validate proof form correctly', () => {
    component.proofRecipientName = '';
    component.selectedFile = null;
    expect(component.isProofFormValid()).toBe(false);

    component.proofRecipientName = 'Sunil';
    expect(component.isProofFormValid()).toBe(true);

    component.proofRecipientName = '';
    component.selectedFile = new File(['dummy'], 'photo.jpg', { type: 'image/jpeg' });
    expect(component.isProofFormValid()).toBe(true);
  });

  it('should submit proof upload and mark delivery DELIVERED', fakeAsync(() => {
    const outForDelivery = component.filteredDeliveries().find((d) => d.status === 'OUT_FOR_DELIVERY');
    expect(outForDelivery).toBeTruthy();

    if (outForDelivery) {
      component.openProofModal(outForDelivery);
      component.selectedFile = new File(['dummy-bytes'], 'proof.jpg', { type: 'image/jpeg' });
      component.proofRecipientName = 'Sunil Weerasinghe';

      component.submitProof();
      tick();

      // 1. Multipart POST upload
      const postReq = httpMock.expectOne(`/api/v1/courier/deliveries/${outForDelivery.parcelId}/proof`);
      expect(postReq.request.method).toBe('POST');
      postReq.flush({
        proofId: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        parcelId: outForDelivery.parcelId,
        proofUrl: '/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6',
        mimeType: 'image/jpeg',
        fileSizeBytes: 11,
        uploadedAt: '2026-10-28T14:30:00Z',
      });
      tick();

      // 2. Status update PATCH to DELIVERED
      const patchReq = httpMock.expectOne(`/api/v1/courier/deliveries/${outForDelivery.parcelId}/status`);
      expect(patchReq.request.method).toBe('PATCH');
      expect(patchReq.request.body.status).toBe('DELIVERED');
      expect(patchReq.request.body.recipientName).toBe('Sunil Weerasinghe');
      patchReq.flush({
        parcelId: outForDelivery.parcelId,
        trackingNumber: outForDelivery.trackingNumber,
        previousStatus: 'OUT_FOR_DELIVERY',
        currentStatus: 'DELIVERED',
        updatedAt: '2026-10-28T14:31:00Z',
        allowedNextStatuses: [],
      });
      tick();

      expect(component.selectedDeliveryForProof()).toBeNull();
      const updated = component.filteredDeliveries().find((d) => d.parcelId === outForDelivery.parcelId);
      expect(updated?.status).toBe('DELIVERED');
    }
  }));

  it('should submit a failure through the profile endpoint and refresh deliveries', fakeAsync(() => {
    const delivery = component.filteredDeliveries()[0];
    component.openFailModal(delivery);

    expect(component.selectedDeliveryForFailure()).toBe(delivery);
    component.failureReasonCategory = 'CUSTOMER_REJECTED';
    component.failureNotes = 'Customer refused parcel';

    component.submitFailure();
    tick();

    const postReq = httpMock.expectOne(`/api/v1/courier/deliveries/${delivery.parcelId}/fail`);
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({ reasonCode: 'CUSTOMER_REJECTED', note: 'Customer refused parcel' });
    postReq.flush(null, { status: 202, statusText: 'Accepted' });
    const refreshReq = httpMock.expectOne('/api/v1/courier/deliveries');
    refreshReq.flush({ ...mockDeliveriesResponse, deliveries: mockDeliveriesResponse.deliveries.map((item) =>
      item.parcelId === delivery.parcelId ? { ...item, status: 'FAILED' as const } : item),
    });
    tick();

    expect(component.selectedDeliveryForFailure()).toBeNull();
    const updated = component.filteredDeliveries().find((d) => d.parcelId === delivery.parcelId);
    expect(updated?.status).toBe('FAILED');
  }));

  it('requires a note for OTHER without making a request', () => {
    const delivery = component.filteredDeliveries()[0];
    component.openFailModal(delivery);
    component.failureReasonCategory = 'OTHER';
    component.failureNotes = '   ';

    component.submitFailure();

    httpMock.expectNone(`/api/v1/courier/deliveries/${delivery.parcelId}/fail`);
    expect(component.selectedDeliveryForFailure()).toBe(delivery);
    expect(component.isSubmittingFailure).toBe(false);
  });

  it('prevents duplicate failure submissions and preserves form values after an API error', fakeAsync(() => {
    const delivery = component.filteredDeliveries()[0];
    component.openFailModal(delivery);
    component.failureReasonCategory = 'INCORRECT_ADDRESS';
    component.failureNotes = 'Address could not be located';

    component.submitFailure();
    component.submitFailure();
    const request = httpMock.expectOne(`/api/v1/courier/deliveries/${delivery.parcelId}/fail`);
    expect(component.isSubmittingFailure).toBe(true);
    request.flush({ message: 'Parcel is no longer assigned' }, { status: 409, statusText: 'Conflict' });
    tick();

    expect(component.isSubmittingFailure).toBe(false);
    expect(component.selectedDeliveryForFailure()).toBe(delivery);
    expect(component.failureReasonCategory).toBe('INCORRECT_ADDRESS');
    expect(component.failureNotes).toBe('Address could not be located');
    expect(component.toastMessage()).toContain('Parcel is no longer assigned');
  }));

  it('should open proof view modal and fetch proof photo when viewProofDetail is called', fakeAsync(() => {
    const delivery = {
      ...component.filteredDeliveries()[0],
      status: 'DELIVERED' as const,
      proof: {
        id: '3fa85f64-5717-4562-b3fc-2c963f66afa6',
        parcelId: 'pcl-55001',
        courierId: 'courier-1',
        fileName: 'proof.jpg',
        fileSize: 1024,
        recipientName: 'Kamal Perera',
        uploadedAt: '2026-10-28T14:30:00Z',
        proofUrl: '/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6',
      },
    };

    component.viewProofDetail(delivery);
    expect(component.selectedDeliveryForProofView()).toEqual(delivery);
    expect(component.isLoadingProofImage()).toBe(true);

    const getReq = httpMock.expectOne('/api/v1/courier/proofs/3fa85f64-5717-4562-b3fc-2c963f66afa6');
    expect(getReq.request.method).toBe('GET');
    getReq.flush(new Blob(['test-photo'], { type: 'image/jpeg' }));
    tick();

    expect(component.isLoadingProofImage()).toBe(false);
    expect(component.viewProofImageUrl()).toBeTruthy();

    component.closeProofViewModal();
    expect(component.selectedDeliveryForProofView()).toBeNull();
    expect(component.viewProofImageUrl()).toBeNull();
  }));
});
