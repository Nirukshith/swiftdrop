import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CustomerParcelsService } from './customer-parcels.service';
import { ParcelPage } from './customer-history.model';

describe('CustomerParcelsService', () => {
  let service: CustomerParcelsService;
  let http: HttpTestingController;

  const page: ParcelPage = { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CustomerParcelsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the first page of 20 by default', () => {
    service.list().subscribe((result) => expect(result).toEqual(page));

    const req = http.expectOne((r) => r.url === '/api/v1/parcels');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('status')).toBe(false);
    req.flush(page);
  });

  it('sends page, size and status when given', () => {
    service.list({ page: 2, size: 10, status: 'DELIVERED' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/parcels');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('status')).toBe('DELIVERED');
    req.flush(page);
  });

  it('leaves out a null status', () => {
    service.list({ status: null }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/parcels');
    expect(req.request.params.has('status')).toBe(false);
    req.flush(page);
  });
});
