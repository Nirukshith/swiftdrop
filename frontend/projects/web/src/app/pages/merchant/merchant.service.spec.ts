import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MerchantOrderListPage, OrderCounts } from './merchant.model';
import { MerchantService } from './merchant.service';

describe('MerchantService', () => {
  let service: MerchantService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MerchantService,
      ],
    });

    service = TestBed.inject(MerchantService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches paginated orders with default parameters', async () => {
    const mockResponse: MerchantOrderListPage = {
      content: [
        {
          orderId: 'ord-1',
          parcelId: 'pcl-1',
          trackingNumber: 'SD-20261028-0001',
          status: 'CREATED',
          fee: 500.0,
          createdAt: '2026-10-28T10:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    };

    const ordersPromise = firstValueFrom(service.getOrders());

    const req = httpMock.expectOne((r) => r.url === '/api/v1/merchant/orders');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toHaveLength(0);

    req.flush(mockResponse);

    const result = await ordersPromise;
    expect(result.content).toHaveLength(1);
    expect(result.content[0].trackingNumber).toBe('SD-20261028-0001');
    expect(result.totalElements).toBe(1);
  });

  it('passes status, search, date range and pagination query parameters', async () => {
    const mockResponse: MerchantOrderListPage = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 1,
      size: 10,
    };

    const ordersPromise = firstValueFrom(
      service.getOrders({
        status: 'DELIVERED',
        trackingNumber: 'SD-20261028',
        from: '2026-10-01T00:00:00Z',
        to: '2026-10-31T23:59:59Z',
        page: 1,
        size: 10,
      })
    );

    const req = httpMock.expectOne((r) => r.url === '/api/v1/merchant/orders');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('DELIVERED');
    expect(req.request.params.get('trackingNumber')).toBe('SD-20261028');
    expect(req.request.params.get('from')).toBe('2026-10-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-10-31T23:59:59Z');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');

    req.flush(mockResponse);

    const result = await ordersPromise;
    expect(result.totalElements).toBe(0);
  });

  it('fetches order status counts from the counts endpoint', async () => {
    const mockCounts: OrderCounts = {
      total: 15,
      created: 3,
      pickedUp: 2,
      inTransit: 4,
      outForDelivery: 1,
      delivered: 4,
      failed: 1,
      cancelled: 0,
    };

    const countsPromise = firstValueFrom(service.getOrderCounts());

    const req = httpMock.expectOne('/api/v1/merchant/orders/counts');
    expect(req.request.method).toBe('GET');

    req.flush(mockCounts);

    const result = await countsPromise;
    expect(result.total).toBe(15);
    expect(result.delivered).toBe(4);
    expect(result.inTransit).toBe(4);
  });
});
