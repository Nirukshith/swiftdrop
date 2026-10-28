import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CustomerComponent } from './customer.component';
import { CustomerService } from './customer/customer.service';

describe('CustomerComponent', () => {
  let component: CustomerComponent;
  let fixture: ComponentFixture<CustomerComponent>;
  let customerService: CustomerService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerComponent],
      providers: [CustomerService],
    }).compileComponents();

    fixture = TestBed.createComponent(CustomerComponent);
    component = fixture.componentInstance;
    customerService = TestBed.inject(CustomerService);
    fixture.detectChanges();
  });

  it('should create the customer component', () => {
    expect(component).toBeTruthy();
  });

  it('should load initial parcels and addresses', fakeAsync(() => {
    tick(100);
    expect(component.parcels().length).toBeGreaterThan(0);
    expect(component.addresses().length).toBeGreaterThan(0);
  }));

  it('should switch tabs between deliveries, wizard, and addresses', () => {
    expect(component.activeTab()).toBe('deliveries');

    component.activeTab.set('wizard');
    expect(component.activeTab()).toBe('wizard');

    component.activeTab.set('addresses');
    expect(component.activeTab()).toBe('addresses');
  });

  it('should filter parcels by status', fakeAsync(() => {
    component.selectedStatusFilter.set('DELIVERED');
    component.loadParcels();
    tick(100);

    expect(component.parcels().every((p) => p.status === 'DELIVERED')).toBe(true);
  }));

  it('should navigate through wizard steps with validation and fee calculation', () => {
    component.startNewDelivery();
    expect(component.wizardStep()).toBe(1);
    expect(component.isStep1Valid()).toBe(true);

    component.goToStep(2);
    expect(component.wizardStep()).toBe(2);
    expect(component.isStep2Valid()).toBe(true);

    component.goToStep(3);
    expect(component.wizardStep()).toBe(3);
    expect(component.feePreview()).toBeTruthy();
    expect(component.feePreview()?.totalFee).toBeGreaterThan(0);

    // Verify fee preview responds to district and size
    component.wizardData.pickupAddress.district = 'Colombo';
    component.wizardData.dropoffAddress.district = 'Kandy';
    component.wizardData.size = 'LARGE';
    component.wizardData.weightKg = 4.0;
    component.updateFeePreview();

    // Base 350 + Inter-district 150 + Weight (4-1)*100 = 300 + Large 250 = 1050
    expect(component.feePreview()?.totalFee).toBe(1050);
  });

  it('should quick-fill address from address book in wizard', fakeAsync(() => {
    tick(100);
    const firstAddr = component.addresses()[0];
    component.selectSavedAddress('pickup', firstAddr.id);

    expect(component.wizardData.pickupAddress.line1).toBe(firstAddr.line1);
    expect(component.wizardData.pickupAddress.district).toBe(firstAddr.district);
  }));

  it('should submit delivery order and receive confirmed tracking number', fakeAsync(() => {
    component.startNewDelivery();
    component.goToStep(4);

    expect(component.orderConfirmed()).toBe(false);
    component.submitDeliveryOrder();
    tick(200);

    expect(component.orderConfirmed()).toBe(true);
    expect(component.confirmedParcel()).toBeTruthy();
    expect(component.confirmedParcel()?.trackingNumber).toMatch(/^SD-\d{8}-[A-Z0-9]+$/);
    expect(component.notification()).toContain('booked successfully');
  }));

  it('should cancel a CREATED parcel', fakeAsync(() => {
    tick(100);
    const createdParcel = component.parcels().find((p) => p.status === 'CREATED');
    if (createdParcel) {
      component.cancelParcel(createdParcel.id);
      tick(100);

      const target = component.parcels().find((p) => p.id === createdParcel.id);
      expect(target?.status).toBe('CANCELLED');
      expect(component.notification()).toContain('cancelled');
    }
  }));

  it('should add a new address to address book', fakeAsync(() => {
    component.openAddressModal();
    expect(component.showAddressModal()).toBe(true);

    component.newAddressForm = {
      label: 'Beach Villa',
      line1: '40 Galle Road',
      line2: '',
      city: 'Bentota',
      district: 'Galle',
      postalCode: '80500',
      isDefault: false,
    };

    expect(component.isAddressFormValid()).toBe(true);
    component.saveNewAddress();
    tick(100);

    expect(component.showAddressModal()).toBe(false);
    expect(component.addresses().some((a) => a.label === 'Beach Villa')).toBe(true);
    expect(component.notification()).toContain('Beach Villa');
  }));

  it('should set an address as default', fakeAsync(() => {
    tick(100);
    const nonDefault = component.addresses().find((a) => !a.isDefault);
    if (nonDefault) {
      component.setDefaultAddress(nonDefault.id);
      tick(100);

      const updated = component.addresses().find((a) => a.id === nonDefault.id);
      expect(updated?.isDefault).toBe(true);
      expect(component.notification()).toContain('default address');
    }
  }));

  it('should delete an address from address book', fakeAsync(() => {
    tick(100);
    const countBefore = component.addresses().length;
    const target = component.addresses()[0];

    component.deleteAddress(target.id);
    tick(100);

    expect(component.addresses().length).toBe(countBefore - 1);
    expect(component.addresses().some((a) => a.id === target.id)).toBe(false);
  }));
});
