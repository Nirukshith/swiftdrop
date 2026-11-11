import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { MerchantComponent } from './merchant.component';
import { MerchantOrderListPage, OrderCounts, WebhookSettings } from './merchant/merchant.model';
import { MerchantService } from './merchant/merchant.service';

describe('MerchantComponent', () => {
  let component: MerchantComponent;
  let fixture: ComponentFixture<MerchantComponent>;
  let merchantService: jest.Mocked<MerchantService>;

  const mockOrderPage: MerchantOrderListPage = {
    content: [
      {
        orderId: 'ord-101',
        parcelId: 'pcl-101',
        trackingNumber: 'SD-20261028-101',
        status: 'IN_TRANSIT',
        recipientName: 'Amara Fernando',
        recipientAddress: 'Colombo 03',
        fee: 750.0,
        createdAt: '2026-10-28T12:00:00Z',
      },
      {
        orderId: 'ord-102',
        parcelId: 'pcl-102',
        trackingNumber: 'SD-20261028-102',
        status: 'DELIVERED',
        recipientName: 'Kavinda Silva',
        recipientAddress: 'Kandy',
        fee: 900.0,
        createdAt: '2026-10-28T14:00:00Z',
      },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 20,
  };

  const mockCounts: OrderCounts = {
    total: 10,
    created: 2,
    pickedUp: 1,
    inTransit: 2,
    outForDelivery: 1,
    delivered: 3,
    failed: 1,
    cancelled: 0,
  };

  beforeEach(async () => {
    const serviceMock: Partial<jest.Mocked<MerchantService>> = {
      getOrders: jest.fn().mockReturnValue(of(mockOrderPage)),
      getOrderCounts: jest.fn().mockReturnValue(of(mockCounts)),
      getWebhookSettings: jest.fn().mockReturnValue(
        of({
          merchantId: 'merchant-test-id',
          url: 'https://api.testmerchant.com/webhook',
          active: true,
          subscribedEvents: ['PARCEL_STATUS_CHANGED'],
          secretConfigured: true,
          secretPreview: 'whsec_••••••••••••••••3f8a',
          rawSecret: null,
          createdAt: '2026-10-20T10:00:00Z',
          updatedAt: '2026-10-25T10:00:00Z',
        })
      ),
      getDeliveryLogs: jest.fn().mockReturnValue(of([])),
      updateWebhookSettings: jest.fn().mockReturnValue(
        of({
          merchantId: 'merchant-test-id',
          url: 'https://api.testmerchant.com/webhook',
          active: true,
          subscribedEvents: ['PARCEL_STATUS_CHANGED'],
          secretConfigured: true,
          secretPreview: 'whsec_••••••••••••••••3f8a',
          rawSecret: null,
          createdAt: '2026-10-20T10:00:00Z',
          updatedAt: '2026-10-28T10:00:00Z',
        })
      ),
      triggerTestWebhook: jest.fn().mockReturnValue(
        of({
          success: true,
          statusCode: 200,
          latencyMs: 85,
          attemptedAt: '2026-10-28T10:00:00Z',
          targetUrl: 'https://api.testmerchant.com/webhook',
          signatureSent: 't=123,v1=abc',
          timestampSent: '123',
          responseBodySummary: '{"ok":true}',
          errorMessage: null,
        })
      ),
      retryDelivery: jest.fn().mockReturnValue(
        of({
          deliveryId: 'del-1',
          eventId: 'evt-1',
          eventType: 'PARCEL_STATUS_CHANGED',
          trackingNumber: 'SD-1',
          targetUrl: 'https://api.testmerchant.com/webhook',
          status: 'SUCCESS',
          httpStatusCode: 200,
          attempts: 2,
          createdAt: '2026-10-28T10:00:00Z',
        })
      ),
    };

    await TestBed.configureTestingModule({
      imports: [MerchantComponent],
      providers: [{ provide: MerchantService, useValue: serviceMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(MerchantComponent);
    component = fixture.componentInstance;
    merchantService = TestBed.inject(MerchantService) as jest.Mocked<MerchantService>;
    fixture.detectChanges();
  });

  it('should create the merchant component and load initial orders and counts', () => {
    expect(component).toBeTruthy();
    expect(merchantService.getOrders).toHaveBeenCalled();
    expect(merchantService.getOrderCounts).toHaveBeenCalled();
    expect(component.orders()).toHaveLength(2);
    expect(component.counts().total).toBe(10);
    expect(component.isLoadingOrders()).toBe(false);
  });

  it('should switch tabs between orders, settings, and deliveries', () => {
    expect(component.activeTab()).toBe('orders');

    component.activeTab.set('settings');
    expect(component.activeTab()).toBe('settings');

    component.activeTab.set('deliveries');
    expect(component.activeTab()).toBe('deliveries');
  });

  it('should reload orders on status filter change with page reset to 0', () => {
    component.onStatusFilterChange('DELIVERED');

    expect(component.selectedStatusFilter()).toBe('DELIVERED');
    expect(component.currentPage()).toBe(0);
    expect(merchantService.getOrders).toHaveBeenCalledWith(
      expect.objectContaining({
        status: 'DELIVERED',
        page: 0,
      })
    );
  });

  it('should reload orders on search query change', () => {
    component.searchQuery = 'SD-20261028-101';
    component.onSearchChange();

    expect(component.currentPage()).toBe(0);
    expect(merchantService.getOrders).toHaveBeenCalledWith(
      expect.objectContaining({
        trackingNumber: 'SD-20261028-101',
        page: 0,
      })
    );
  });

  it('should reload orders on date filter change', () => {
    component.fromDate = '2026-10-01';
    component.toDate = '2026-10-28';
    component.onDateFilterChange();

    expect(component.currentPage()).toBe(0);
    expect(merchantService.getOrders).toHaveBeenCalledWith(
      expect.objectContaining({
        from: expect.any(String),
        to: expect.any(String),
        page: 0,
      })
    );
  });

  it('should navigate to another page when onPageChange is called', () => {
    component.onPageChange(2);

    expect(component.currentPage()).toBe(2);
    expect(merchantService.getOrders).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 2,
      })
    );
  });

  it('should reset filters when resetFilters is clicked', () => {
    component.selectedStatusFilter.set('FAILED');
    component.searchQuery = 'test';
    component.fromDate = '2026-10-01';
    component.toDate = '2026-10-28';

    component.resetFilters();

    expect(component.selectedStatusFilter()).toBe('ALL');
    expect(component.searchQuery).toBe('');
    expect(component.fromDate).toBe('');
    expect(component.toDate).toBe('');
    expect(component.currentPage()).toBe(0);
  });

  it('should handle API errors with orderError and stop loading', () => {
    merchantService.getOrders.mockReturnValue(throwError(() => new Error('Server error')));

    component.loadOrders(0);

    expect(component.isLoadingOrders()).toBe(false);
    expect(component.orderError()).toContain('Unable to load merchant orders');
  });

  it('should render empty state when no orders returned', () => {
    merchantService.getOrders.mockReturnValue(
      of({
        content: [],
        totalElements: 0,
        totalPages: 0,
        page: 0,
        size: 20,
      })
    );

    component.loadOrders(0);

    expect(component.orders()).toHaveLength(0);
    expect(component.totalElements()).toBe(0);
  });

  it('shows loading and uses only the latest tracking response after a filter change', () => {
    const pending = new Subject<MerchantOrderListPage>();
    merchantService.getOrders
      .mockReturnValueOnce(pending.asObservable())
      .mockReturnValueOnce(of({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 }));

    component.loadOrders();
    fixture.detectChanges();
    expect(component.isLoadingOrders()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Loading merchant orders');

    component.onStatusFilterChange('DELIVERED');
    pending.next(mockOrderPage);
    fixture.detectChanges();

    expect(component.isLoadingOrders()).toBe(false);
    expect(component.orders()).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain('No orders found');
  });

  it('shows a counts error and recovers when the merchant retries', () => {
    merchantService.getOrderCounts.mockReturnValueOnce(throwError(() => new Error('Unavailable')));
    component.loadOrderCounts();
    fixture.detectChanges();

    expect(component.countsError()).toContain('Unable to load order counts');
    expect(fixture.nativeElement.textContent).toContain('Unable to load order counts');

    merchantService.getOrderCounts.mockReturnValueOnce(of(mockCounts));
    component.loadOrderCounts();
    fixture.detectChanges();

    expect(component.countsError()).toBeNull();
    expect(component.counts().total).toBe(10);
    expect(fixture.nativeElement.textContent).toContain('Total Orders');
  });

  it('refreshes both merchant status rows and aggregate counts', () => {
    const ordersCalls = merchantService.getOrders.mock.calls.length;
    const countsCalls = merchantService.getOrderCounts.mock.calls.length;

    component.refreshOrders();

    expect(merchantService.getOrders).toHaveBeenCalledTimes(ordersCalls + 1);
    expect(merchantService.getOrderCounts).toHaveBeenCalledTimes(countsCalls + 1);
  });

  it('should save webhook settings and update notification', () => {
    component.settingsForm.url = 'https://api.testmerchant.com/webhook';
    component.settingsForm.active = true;

    component.saveSettings();

    expect(merchantService.updateWebhookSettings).toHaveBeenCalled();
    expect(component.notification()).toContain('saved successfully');
  });

  it('should run test webhook and display result', () => {
    component.testUrlOverride = 'https://webhook.site/test-spec';
    component.runTestWebhook();

    expect(component.isTestingWebhook()).toBe(false);
    expect(component.testResult()).toBeTruthy();
    expect(component.testResult()?.success).toBe(true);
  });

  it('blocks duplicate saves until the API responds', () => {
    const pending = new Subject<WebhookSettings>();
    merchantService.updateWebhookSettings.mockReturnValue(pending);
    component.saveSettings();
    component.saveSettings();
    expect(merchantService.updateWebhookSettings).toHaveBeenCalledTimes(1);
    expect(component.isSavingSettings()).toBe(true);
    pending.next({ ...component.settings()!, updatedAt: '2026-11-11T10:00:00Z' });
    pending.complete();
    expect(component.isSavingSettings()).toBe(false);
  });

  it('shows validation errors without submitting an invalid URL', () => {
    component.settingsForm.url = 'http://localhost/private';
    component.saveSettings();
    expect(merchantService.updateWebhookSettings).not.toHaveBeenCalled();
    expect(component.settingsError()).toContain('HTTPS');
  });

  it('shows a failed test delivery returned in the 502 response body', () => {
    merchantService.triggerTestWebhook.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 502,
      error: { success: false, statusCode: 500, latencyMs: 50,
        attemptedAt: '2026-11-11T10:00:00Z', targetUrl: 'https://merchant.example/webhook',
        signatureSent: 't=1,v1=abc', timestampSent: '1', errorMessage: 'Target returned HTTP 500' },
    })));
    component.runTestWebhook();
    expect(component.testResult()?.success).toBe(false);
    expect(component.testResult()?.statusCode).toBe(500);
    expect(component.isTestingWebhook()).toBe(false);
  });

  it('clears the one-time secret on leaving settings', () => {
    component.settings.set({ ...component.settings()!, rawSecret: 'one-time-secret' });
    component.selectTab('orders');
    expect(component.settings()?.rawSecret).toBeNull();
  });
});
