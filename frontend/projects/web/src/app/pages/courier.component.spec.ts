import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourierComponent } from './courier.component';
import { CourierService } from './courier/courier.service';

describe('CourierComponent', () => {
  let component: CourierComponent;
  let fixture: ComponentFixture<CourierComponent>;
  let courierService: CourierService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CourierComponent],
      providers: [
        CourierService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CourierComponent);
    component = fixture.componentInstance;
    courierService = TestBed.inject(CourierService);
    fixture.detectChanges();
  });

  it('should create the courier component', () => {
    expect(component).toBeTruthy();
  });

  it('should display initial deliveries and stats', () => {
    expect(component.filteredDeliveries().length).toBeGreaterThan(0);
    expect(component.stats().total).toBe(component.filteredDeliveries().length);
  });

  it('should filter deliveries when changing filter tab', () => {
    // ACTIVE tab
    component.activeFilter.set('ACTIVE');
    const active = component.filteredDeliveries();
    expect(
      active.every((d) => ['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT'].includes(d.status))
    ).toBe(true);

    // DELIVERED tab
    component.activeFilter.set('DELIVERED');
    const delivered = component.filteredDeliveries();
    expect(delivered.every((d) => d.status === 'DELIVERED')).toBe(true);

    // FAILED tab
    component.activeFilter.set('FAILED');
    const failed = component.filteredDeliveries();
    expect(failed.every((d) => d.status === 'FAILED_DELIVERY')).toBe(true);
  });

  it('should filter deliveries by search query', () => {
    component.searchQuery = 'Sunil';
    const results = component.filteredDeliveries();
    expect(results.length).toBeGreaterThan(0);
    expect(results[0].recipientName).toContain('Sunil');
  });

  it('should advance delivery status on action click', fakeAsync(() => {
    // Find delivery with status ASSIGNED
    const assigned = component
      .filteredDeliveries()
      .find((d) => d.status === 'ASSIGNED');
    expect(assigned).toBeTruthy();

    if (assigned) {
      component.advanceStatus(assigned, 'PICKED_UP');
      tick(100);

      const updated = courierService.getDeliveryById(assigned.parcelId);
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
    expect(component.isProofFormValid()).toBe(false);

    component.selectedFile = new File(['dummy'], 'photo.jpg', { type: 'image/jpeg' });
    expect(component.isProofFormValid()).toBe(true);
  });

  it('should open fail modal and record failure status', fakeAsync(() => {
    const delivery = component.filteredDeliveries()[0];
    component.openFailModal(delivery);

    expect(component.selectedDeliveryForFailure()).toBe(delivery);
    component.failureReasonCategory = 'Customer refused delivery';
    component.failureNotes = 'Item was damaged';

    component.submitFailure();
    tick(100);

    expect(component.selectedDeliveryForFailure()).toBeNull();
    const updated = courierService.getDeliveryById(delivery.parcelId);
    expect(updated?.status).toBe('FAILED_DELIVERY');
    expect(updated?.failureReason).toContain('Customer refused delivery');
  }));
});
