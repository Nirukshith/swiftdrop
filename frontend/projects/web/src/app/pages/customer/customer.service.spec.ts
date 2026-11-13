import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateCustomerParcelRequest, CustomerAddress, CustomerParcel, CustomerParcelPage, QuoteResponse } from './customer.model';
import { CustomerService } from './customer.service';

describe('CustomerService', () => {
  let service: CustomerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CustomerService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(CustomerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch fee quote with correct query parameters', () => {
    const mockQuote: QuoteResponse = {
      currency: 'LKR',
      totalFee: 850,
      breakdown: {
        dropoffZoneBaseFee: 350,
        weightBandCharge: 250,
        sizeCharge: 250,
      },
      pickupZone: 'Western',
      dropoffZone: 'Central',
    };

    service.getFeeQuote('Colombo', 'Kandy', 2.5, 'MEDIUM').subscribe((quote) => {
      expect(quote).toEqual(mockQuote);
      expect(quote.totalFee).toBe(850);
      expect(quote.breakdown.dropoffZoneBaseFee).toBe(350);
    });

    const req = httpMock.expectOne((r) =>
      r.url === '/api/v1/parcels/quote' &&
      r.params.get('pickupDistrict') === 'Colombo' &&
      r.params.get('dropoffDistrict') === 'Kandy' &&
      r.params.get('weightKg') === '2.5' &&
      r.params.get('size') === 'MEDIUM'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockQuote);
  });

  it('should create parcel via POST /api/v1/parcels', () => {
    const request: CreateCustomerParcelRequest = {
      pickupAddress: {
        line1: '142 Havelock Rd',
        city: 'Colombo 05',
        district: 'Colombo',
        postalCode: '00500',
      },
      dropoffAddress: {
        line1: '88 Kandy Rd',
        city: 'Kadugannawa',
        district: 'Kandy',
        postalCode: '20300',
      },
      weightKg: 2.5,
      size: 'MEDIUM',
      recipientName: 'Sunil Silva',
      recipientPhone: '+94771234567',
      notes: 'Fragile',
    };

    const mockCreated: CustomerParcel = {
      id: 'pcl-new-999',
      trackingNumber: 'SD-20261105-X9Y8Z7',
      status: 'CREATED',
      pickupAddress: request.pickupAddress,
      dropoffAddress: request.dropoffAddress,
      weightKg: 2.5,
      size: 'MEDIUM',
      fee: 850,
      attemptCount: 0,
      recipientName: request.recipientName,
      recipientPhone: request.recipientPhone,
      notes: request.notes,
      createdAt: new Date().toISOString(),
    };

    service.createParcel(request).subscribe((created) => {
      expect(created.id).toBe('pcl-new-999');
      expect(created.trackingNumber).toBe('SD-20261105-X9Y8Z7');
      expect(created.status).toBe('CREATED');
    });

    const req = httpMock.expectOne('/api/v1/parcels');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockCreated);

  });

  it('should list parcels from the authenticated customer endpoint', () => {
    const page: CustomerParcelPage = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    };

    service.listParcels('CREATED', 2, 10).subscribe((parcels) => {
      expect(parcels.content).toEqual([]);
      expect(parcels.page).toBe(2);
    });

    const req = httpMock.expectOne((request) =>
      request.url === '/api/v1/parcels' &&
      request.params.get('status') === 'CREATED' &&
      request.params.get('page') === '2' &&
      request.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(page);
  });

  it('should manage addresses through the authenticated address endpoint', () => {
    const address: CustomerAddress = {
      id: 'address-1',
      customerId: 'customer-1',
      label: 'Warehouse',
      line1: '10 Port Access Rd',
      line2: null,
      city: 'Colombo 13',
      district: 'Colombo',
      postalCode: '01300',
      isDefault: false,
    };

    service.listAddresses().subscribe((addresses) => expect(addresses).toEqual([address]));
    httpMock.expectOne('/api/v1/addresses').flush([address]);

    service.createAddress({
      label: 'Warehouse',
      line1: '10 Port Access Rd',
      city: 'Colombo 13',
      district: 'Colombo',
      postalCode: '01300',
      isDefault: false,
    }).subscribe((saved) => expect(saved).toEqual(address));
    const req = httpMock.expectOne('/api/v1/addresses');
    expect(req.request.method).toBe('POST');
    req.flush(address, { status: 201, statusText: 'Created' });
  });

  it('should update an address and set a default through the address endpoint', () => {
    const address: CustomerAddress = {
      id: 'address-1',
      customerId: 'customer-1',
      label: 'Home',
      line1: '142 Havelock Road',
      line2: null,
      city: 'Colombo 05',
      district: 'Colombo',
      postalCode: '00500',
      isDefault: false,
    };
    const changes = { ...address, isDefault: true };

    service.updateAddress(address.id, changes).subscribe((updated) => {
      expect(updated.isDefault).toBe(true);
    });
    const updateRequest = httpMock.expectOne('/api/v1/addresses/address-1');
    expect(updateRequest.request.method).toBe('PUT');
    updateRequest.flush(changes);

    service.setDefaultAddress(address.id).subscribe((updated) => {
      expect(updated.isDefault).toBe(true);
    });
    httpMock.expectOne('/api/v1/addresses').flush([address]);
    const defaultRequest = httpMock.expectOne('/api/v1/addresses/address-1');
    expect(defaultRequest.request.method).toBe('PUT');
    expect(defaultRequest.request.body.isDefault).toBe(true);
    defaultRequest.flush(changes);
  });

  it('should cancel a parcel through the parcel endpoint', () => {
    const parcel = { id: 'parcel-1' } as CustomerParcel;
    service.cancelParcel(parcel.id, 'Customer changed plans').subscribe((cancelled) => {
      expect(cancelled).toEqual(parcel);
    });

    const req = httpMock.expectOne('/api/v1/parcels/parcel-1/cancel');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Customer changed plans' });
    req.flush(parcel);
  });
});

