import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MerchantComponent } from './merchant.component';
import { MerchantService } from './merchant/merchant.service';

describe('MerchantComponent', () => {
  let component: MerchantComponent;
  let fixture: ComponentFixture<MerchantComponent>;
  let merchantService: MerchantService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MerchantComponent],
      providers: [MerchantService],
    }).compileComponents();

    fixture = TestBed.createComponent(MerchantComponent);
    component = fixture.componentInstance;
    merchantService = TestBed.inject(MerchantService);
    fixture.detectChanges();
  });

  it('should create the merchant component', () => {
    expect(component).toBeTruthy();
  });

  it('should load initial orders and counts', () => {
    expect(component.orders().length).toBeGreaterThan(0);
    expect(component.counts().total).toBeGreaterThan(0);
  });

  it('should switch tabs between orders, settings, and deliveries', () => {
    expect(component.activeTab()).toBe('orders');

    component.activeTab.set('settings');
    expect(component.activeTab()).toBe('settings');

    component.activeTab.set('deliveries');
    expect(component.activeTab()).toBe('deliveries');
  });

  it('should save webhook settings and update notification', () => {
    component.settingsForm.url = 'https://api.testmerchant.com/webhook';
    component.settingsForm.active = true;

    component.saveSettings();

    expect(component.settings()?.url).toBe('https://api.testmerchant.com/webhook');
    expect(component.notification()).toContain('saved successfully');
  });

  it('should run test webhook and display result', (done) => {
    component.testUrlOverride = 'https://webhook.site/test-spec';
    component.runTestWebhook();

    expect(component.isTestingWebhook()).toBe(true);

    setTimeout(() => {
      expect(component.isTestingWebhook()).toBe(false);
      expect(component.testResult()).toBeTruthy();
      expect(component.testResult()?.success).toBe(true);
      expect(component.testResult()?.statusCode).toBe(200);
      done();
    }, 300);
  });

  it('should filter orders by status', () => {
    component.selectedStatusFilter.set('DELIVERED');
    component.loadOrders();

    const deliveredOrders = component.orders();
    expect(deliveredOrders.every((o) => o.status === 'DELIVERED')).toBe(true);
  });

  it('should retry a failed webhook delivery', (done) => {
    const failedDeliveries = component.deliveries().filter((d) => d.status === 'FAILED');
    if (failedDeliveries.length > 0) {
      const targetId = failedDeliveries[0].deliveryId;
      component.retryDelivery(targetId);

      setTimeout(() => {
        const updated = component.deliveries().find((d) => d.deliveryId === targetId);
        expect(updated?.status).toBe('SUCCESS');
        expect(updated?.httpStatusCode).toBe(200);
        done();
      }, 250);
    } else {
      done();
    }
  });
});
