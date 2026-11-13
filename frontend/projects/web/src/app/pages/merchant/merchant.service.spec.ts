import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { MerchantOrderListPage, OrderCounts, WebhookSettings } from './merchant.model';
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

  const webhookSettings: WebhookSettings = {
    merchantId: '9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
    url: 'https://merchant.example/webhook',
    active: true,
    subscribedEvents: ['PARCEL_STATUS_CHANGED'],
    secretConfigured: true,
    secretPreview: 'whsec_...1234',
    rawSecret: null,
    createdAt: '2026-11-11T10:00:00Z',
    updatedAt: '2026-11-11T10:00:00Z',
  };

  it('loads settings from the authenticated merchant endpoint without identity headers', async () => {
    const result = firstValueFrom(service.getWebhookSettings());
    const req = httpMock.expectOne('/api/v1/merchant/webhooks/settings');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('X-Merchant-Id')).toBe(false);
    req.flush(webhookSettings);
    expect(await result).toEqual(webhookSettings);
  });

  it('saves settings with the merged request shape and accepts a one-time secret', async () => {
    const result = firstValueFrom(service.updateWebhookSettings(
      'https://merchant.example/webhook', true, ['PARCEL_STATUS_CHANGED'], true));
    const req = httpMock.expectOne('/api/v1/merchant/webhooks/settings');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      url: 'https://merchant.example/webhook', active: true,
      subscribedEvents: ['PARCEL_STATUS_CHANGED'], regenerateSecret: true,
    });
    req.flush({ ...webhookSettings, rawSecret: 'one-time-secret' });
    expect((await result).rawSecret).toBe('one-time-secret');
  });

  it('posts a real test request and omits a blank override', async () => {
    const result = firstValueFrom(service.triggerTestWebhook('  '));
    const req = httpMock.expectOne('/api/v1/merchant/webhooks/test');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ success: true, statusCode: 204, latencyMs: 12,
      attemptedAt: '2026-11-11T10:00:00Z', targetUrl: webhookSettings.url,
      signatureSent: 't=1,v1=abc', timestampSent: '1' });
    expect((await result).statusCode).toBe(204);
  });

  it('loads persisted delivery rows instead of manufactured test entries', async () => {
    const result = firstValueFrom(service.getDeliveryLogs('FAILED', 2, 10));
    const req = httpMock.expectOne((request) => request.url === '/api/v1/merchant/webhooks/deliveries');
    expect(req.request.params.get('status')).toBe('FAILED');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], totalElements: 21, totalPages: 3, page: 2, size: 10 });
    expect((await result).totalElements).toBe(21);
  });

  it('passes delivery event and date filters to the delivery log endpoint', async () => {
    const result = firstValueFrom(service.getDeliveryLogs(
      'PENDING', 1, 20, 'PARCEL_STATUS_CHANGED',
      '2026-11-01T00:00:00Z', '2026-11-30T23:59:59Z'
    ));
    const req = httpMock.expectOne((request) => request.url === '/api/v1/merchant/webhooks/deliveries');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('eventType')).toBe('PARCEL_STATUS_CHANGED');
    expect(req.request.params.get('from')).toBe('2026-11-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-11-30T23:59:59Z');
    req.flush({ content: [], totalElements: 0, totalPages: 0, page: 1, size: 20 });
    await result;
  });
});
