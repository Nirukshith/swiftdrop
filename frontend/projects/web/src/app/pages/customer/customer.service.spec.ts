import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateCustomerParcelRequest, CustomerParcel, QuoteResponse } from './customer.model';
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

  it('should create parcel via POST /api/v1/parcels and update parcels signal', () => {
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

    service.listParcels().subscribe((parcels) => {
      expect(parcels[0].id).toBe('pcl-new-999');
      expect(parcels[0].trackingNumber).toBe('SD-20261105-X9Y8Z7');
    });
  });

  it('should manage address book locally', () => {
    service.listAddresses().subscribe((addresses) => {
      expect(addresses.length).toBeGreaterThan(0);
    });

    service.createAddress({
      label: 'Warehouse',
      line1: '10 Port Access Rd',
      city: 'Colombo 13',
      district: 'Colombo',
      postalCode: '01300',
      isDefault: false,
    }).subscribe((saved) => {
      expect(saved.label).toBe('Warehouse');
      expect(saved.id).toBeTruthy();
    });
  });
});

